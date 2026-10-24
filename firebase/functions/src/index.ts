// 꽃신 Cloud Functions. 직접 운영하는 서버는 없고, 여기 있는 함수가 전부다(설계 1장).
//
// - onInboxCreated  : 우편함 새 문서 → 알림 정책(조용한 시간 · 묶음) → FCM
// - morningDigest   : 15분마다, 조용한 시간이 막 끝난 사람에게 "밤사이 소식 N개"
// - onPrefsWritten  : notifyPrefs 저장 시 아침 요약 색인(quietEndUtcMinute) 계산
// - onPairingStep   : 두 사람이 SAS 확인을 마치면 커플 · 기기 · 키 봉투를 만든다
// - onDeviceCreated : 대기 기기가 생기면 진짜 폰에 "새 기기에서 로그인 시도" 알림
// - onRotationApproved : 상대 기기의 서명된 승인 → 새 기기 active, 옛 기기 폐기
// - onRelinkStep    : 상대 폰이 넘겨주기 — 지수 서명 · SAS 확인 → 새 기기 활성화, 옛 기기 폐기
// - finalizeDevice  : 새 기기 키로 서명한 챌린지 확인 → 커스텀 토큰 발급 → 옛 세션 폐기
// - sendFromWidget  : iOS 위젯(App Intent)의 보고싶어. Firebase SDK 없이 서명만으로 받는다
// - kakaoSignIn     : 카카오 액세스 토큰(app_id 확인) → 커스텀 토큰
// - unlinkCouple · deleteAccount : 연결 해제 · 탈퇴
// - onInquiryAnswered : 문의에 답변(관리자가 answer 를 쓰면) → 문의한 사람에게 알림
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { FieldValue, getFirestore, Timestamp } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { getStorage } from 'firebase-admin/storage';
import { defineSecret, defineString } from 'firebase-functions/params';
import { onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { HttpsError, onCall, onRequest } from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { decide, DEFAULT_PREFS, inDigestWindow, INLINE_MAX_BASE64, NotifyPrefs, NotifyState, quietEndUtcMinute } from './policy';
import { approvalMessage, finalizeMessage, opSignedBytes, verifyEd25519 } from './sig';
import { AppleConfig, appleConfigured, exchangeCode, revoke as revokeApple } from './apple';
import { DAY_MS, FREE_KEEP_DAYS, retentionAction, WARN_DAYS, warnMessage } from './retention';
import { entitlementsFrom, usersOf } from './revenuecat';

initializeApp();
const db = getFirestore();

const REGION = 'asia-northeast3'; // 서울
const KAKAO_APP_ID = defineString('KAKAO_APP_ID');
const KAKAO_ADMIN_KEY = defineString('KAKAO_ADMIN_KEY', { default: '' });
// Sign in with Apple 토큰 폐기용. 비어 있으면 보관 · 폐기를 건너뛴다(Apple 로그인 자체는 Firebase Auth 가 한다).
const APPLE_TEAM_ID = defineString('APPLE_TEAM_ID', { default: '' });
const APPLE_KEY_ID = defineString('APPLE_KEY_ID', { default: '' });
const APPLE_CLIENT_ID = defineString('APPLE_CLIENT_ID', { default: '' });
const APPLE_PRIVATE_KEY = defineString('APPLE_PRIVATE_KEY', { default: '' });
// RevenueCat: 웹훅 Authorization 헤더 값 · REST 비밀 키(sk_). Secret Manager 에 둔다(firebase functions:secrets:set).
const REVENUECAT_WEBHOOK_AUTH = defineSecret('REVENUECAT_WEBHOOK_AUTH');
const REVENUECAT_SECRET_KEY = defineSecret('REVENUECAT_SECRET_KEY');
const appleConfig = (): AppleConfig => ({
  teamId: APPLE_TEAM_ID.value(), keyId: APPLE_KEY_ID.value(), clientId: APPLE_CLIENT_ID.value(), privateKey: APPLE_PRIVATE_KEY.value(),
});

const b64 = (s: string) => Buffer.from(s, 'base64');

// ── 공용 ─────────────────────────────────────────────────────────────────

async function coupleOf(coupleId: string) {
  const snap = await db.doc(`couples/${coupleId}`).get();
  if (!snap.exists) throw new HttpsError('not-found', '커플이 없다');
  return snap.data() as { members: Record<string, string> };
}

function uidOfDevice(members: Record<string, string>, deviceId: string): string | undefined {
  return Object.entries(members).find(([, d]) => d === deviceId)?.[0];
}

async function tokensOf(uid: string): Promise<string[]> {
  const snap = await db.doc(`users/${uid}`).get();
  return (snap.get('fcmTokens') as string[] | undefined) ?? [];
}

/**
 * FCM 발송. 화면 알림은 iOS NSE 가 인라인 암호문을 풀어 문구를 바꿀 수 있게 mutable-content 를 켠다.
 * data 만 보낼 때 Android 는 normal priority — 알림 없는 high-priority 는 Android 가 점점 낮춘다.
 */
async function push(uid: string, payload: { visible?: { title: string; body: string }; data: Record<string, string> }) {
  const tokens = await tokensOf(uid);
  if (tokens.length === 0) return;
  const res = await getMessaging().sendEachForMulticast({
    tokens,
    data: payload.data,
    notification: payload.visible,
    android: { priority: payload.visible ? 'high' : 'normal' },
    apns: payload.visible
      ? { payload: { aps: { 'mutable-content': 1, sound: 'default', 'thread-id': payload.data.kind ?? 'kkotsin' } } }
      : { headers: { 'apns-push-type': 'background', 'apns-priority': '5' }, payload: { aps: { 'content-available': 1 } } },
  });
  // 만료된 토큰 정리
  const dead = res.responses
    .map((r, i) => (!r.success && r.error?.code === 'messaging/registration-token-not-registered' ? tokens[i] : null))
    .filter((t): t is string => t !== null);
  if (dead.length) await db.doc(`users/${uid}`).update({ fcmTokens: FieldValue.arrayRemove(...dead) });
}

async function prefsOf(uid: string): Promise<NotifyPrefs> {
  const snap = await db.doc(`users/${uid}/notifyPrefs/main`).get();
  return { ...DEFAULT_PREFS, ...(snap.data() as Partial<NotifyPrefs> | undefined) };
}

// ── 알림 ─────────────────────────────────────────────────────────────────

export const onInboxCreated = onDocumentCreated(
  { document: 'couples/{c}/inbox/{deviceId}/ops/{opId}', region: REGION },
  async (event) => {
    const doc = event.data?.data();
    if (!doc) return;
    const { c, deviceId, opId } = event.params;
    const couple = await coupleOf(c);
    const uid = uidOfDevice(couple.members, deviceId);
    if (!uid) return;
    const senderUid = uidOfDevice(couple.members, doc.from as string);
    const senderName = senderUid ? ((await db.doc(`users/${senderUid}`).get()).get('nickname') as string | undefined) ?? '연인' : '연인';

    const stateRef = db.doc(`notifyState/${uid}`);
    const prefs = await prefsOf(uid);
    const inline = typeof doc.ct === 'string' && doc.ct.length <= INLINE_MAX_BASE64 ? (doc.ct as string) : undefined;

    const decision = await db.runTransaction(async (tx) => {
      const state = ((await tx.get(stateRef)).data() as NotifyState | undefined) ?? { missYouDay: '', missYouCount: 0 };
      const r = decide({ kind: doc.kind, resend: doc.resend === true, inline }, prefs, state, new Date(), senderName);
      tx.set(stateRef, r.state);
      return r.decision;
    });

    const data: Record<string, string> = { kind: String(doc.kind), coupleId: c, opId, v: '1' };
    if (decision.type === 'visible') {
      if (decision.inline) {
        data.ct = decision.inline;
        data.sig = String(doc.sig ?? '');
        data.header = String(doc.header ?? '');
      }
      await push(uid, { visible: { title: decision.title, body: decision.body }, data });
    } else if (decision.type === 'data') {
      await push(uid, { data });
    }
  },
);

export const onPrefsWritten = onDocumentWritten(
  { document: 'users/{uid}/notifyPrefs/{doc}', region: REGION },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;
    const prefs = { ...DEFAULT_PREFS, ...(after.data() as Partial<NotifyPrefs>) };
    const index = quietEndUtcMinute(prefs);
    if (after.get('quietEndUtcMinute') !== index) await after.ref.update({ quietEndUtcMinute: index });
  },
);

export const morningDigest = onSchedule({ schedule: 'every 15 minutes', region: REGION, timeZone: 'Asia/Seoul' }, async () => {
  const now = new Date();
  const nowUtc = now.getUTCHours() * 60 + now.getUTCMinutes();
  const from = (nowUtc - 15 + 1440) % 1440;
  // 자정을 넘는 창은 두 번 나눠 묻는다.
  const ranges = from <= nowUtc ? [[from, nowUtc]] : [[from, 1439], [0, nowUtc]];
  for (const [lo, hi] of ranges) {
    const snap = await db.collectionGroup('notifyPrefs').where('quietEndUtcMinute', '>=', lo).where('quietEndUtcMinute', '<=', hi).get();
    for (const prefsDoc of snap.docs) {
      if (!inDigestWindow(prefsDoc.get('quietEndUtcMinute') as number, nowUtc)) continue;
      const uid = prefsDoc.ref.parent.parent!.id;
      const user = await db.doc(`users/${uid}`).get();
      const coupleId = user.get('coupleId') as string | undefined;
      const deviceId = user.get('deviceId') as string | undefined;
      if (!coupleId || !deviceId) continue;
      const count = (await db.collection(`couples/${coupleId}/inbox/${deviceId}/ops`).where('resend', '!=', true).count().get()).data().count;
      if (count > 0) await push(uid, { visible: { title: '꽃신', body: `밤사이 소식 ${count}개` }, data: { kind: 'digest', coupleId } });
    }
  }
  // 24시간 지난 대기 기기 정리
  const stale = await db.collectionGroup('devices').where('state', '==', 'pending')
    .where('createdAt', '<', Timestamp.fromMillis(Date.now() - 24 * 3600 * 1000)).get();
  await Promise.all(stale.docs.map((d) => d.ref.delete()));
});

// ── 첫 연결 ───────────────────────────────────────────────────────────────

export const onPairingStep = onDocumentCreated({ document: 'pairings/{code}/steps/{name}', region: REGION }, async (event) => {
  const { code, name } = event.params;
  if (!name.startsWith('confirm-')) return;
  const base = db.doc(`pairings/${code}`);
  const [pairing, join, inviter, reveal, ci, cj] = await Promise.all(
    ['', '/steps/join', '/steps/inviter', '/steps/reveal', '/steps/confirm-inviter', '/steps/confirm-joiner'].map((p) => db.doc(`pairings/${code}${p}`).get()),
  );
  if (![pairing, join, inviter, reveal, ci, cj].every((s) => s.exists)) return; // 아직 한쪽만 확인
  if (ci.get('sas') !== cj.get('sas')) {
    // 두 화면의 SAS 가 다르다 → 바꿔치기 의심. 초대를 폐기한다.
    await db.recursiveDelete(base);
    return;
  }
  const inviterUid = pairing.get('uid') as string;
  const joinerUid = join.get('uid') as string;
  const coupleRef = db.collection('couples').doc();
  const batch = db.batch();
  batch.set(coupleRef, { members: { [inviterUid]: inviter.get('deviceId'), [joinerUid]: reveal.get('deviceId') }, linkedAt: FieldValue.serverTimestamp() });
  batch.set(coupleRef.collection('devices').doc(inviter.get('deviceId')), {
    uid: inviterUid, signPub: inviter.get('signPub'), boxPub: inviter.get('boxPub'), state: 'active', active: true, createdAt: FieldValue.serverTimestamp(),
  });
  batch.set(coupleRef.collection('devices').doc(reveal.get('deviceId')), {
    uid: joinerUid, signPub: reveal.get('signPub'), boxPub: reveal.get('boxPub'), state: 'active', active: true, createdAt: FieldValue.serverTimestamp(),
  });
  // 커플 키(epoch 1)는 초대한 폰이 초대받은 폰의 boxPub 으로 봉인해 confirm 에 실어 보냈다.
  batch.set(coupleRef.collection('keys').doc(reveal.get('deviceId')), { wrapped: ci.get('wrappedKey'), epoch: 1 });
  // linkedVia: 이 초대 코드로 생긴 커플이라는 표시. 같은 두 사람이 다시 연결하면 coupleId 가 잠깐 옛 값이라
  // 앱은 linkedVia 가 지금 코드와 같을 때만 coupleId 를 믿는다.
  batch.set(db.doc(`users/${inviterUid}`), { coupleId: coupleRef.id, deviceId: inviter.get('deviceId'), linkedVia: code }, { merge: true });
  batch.set(db.doc(`users/${joinerUid}`), { coupleId: coupleRef.id, deviceId: reveal.get('deviceId'), linkedVia: code }, { merge: true });
  await batch.commit();
  await db.recursiveDelete(base);
  await Promise.all([inviterUid, joinerUid].map((u) => push(u, { data: { kind: 'linked', coupleId: coupleRef.id } })));
});

// ── 기기 교체(7-3-A) ─────────────────────────────────────────────────────

export const onDeviceCreated = onDocumentCreated({ document: 'couples/{c}/devices/{deviceId}', region: REGION }, async (event) => {
  const d = event.data?.data();
  if (!d || d.state !== 'pending') return;
  // 같은 uid 에 대기 기기가 생겼다 → 진짜 폰에 알린다(계정만 털린 경우 알아챌 수 있게).
  await push(d.uid as string, { visible: { title: '꽃신', body: '새 기기에서 로그인 시도가 있었어요' }, data: { kind: 'device-pending', coupleId: event.params.c } });
});

export const onRotationApproved = onDocumentCreated(
  { document: 'couples/{c}/rotations/{r}/approval/{a}', region: REGION },
  async (event) => {
    const { c, r } = event.params;
    const approval = event.data?.data();
    if (!approval) return;
    const rotation = (await db.doc(`couples/${c}/rotations/${r}`).get()).data();
    if (!rotation) return;
    const approver = await db.doc(`couples/${c}/devices/${approval.approverDeviceId}`).get();
    // 승인자는 요청자가 아닌 상대의 활성 기기여야 하고, 그 기기 키로 서명했어야 한다.
    if (!approver.get('active') || approver.get('uid') === rotation.uid) return;
    const ok = verifyEd25519(
      approvalMessage(c, r, rotation.newDeviceId, approval.cutoff ?? {}),
      b64(approval.sig),
      b64(approver.get('signPub')),
    );
    if (!ok) return;
    const batch = db.batch();
    batch.update(db.doc(`couples/${c}/devices/${rotation.newDeviceId}`), { state: 'active', active: true });
    if (rotation.oldDeviceId && rotation.oldDeviceId !== rotation.newDeviceId) {
      batch.update(db.doc(`couples/${c}/devices/${rotation.oldDeviceId}`), { state: 'revoked', active: false, revokedAt: FieldValue.serverTimestamp(), cutoff: approval.cutoff ?? {} });
    }
    batch.update(db.doc(`couples/${c}`), { [`members.${rotation.uid}`]: rotation.newDeviceId });
    batch.set(db.doc(`users/${rotation.uid}`), { deviceId: rotation.newDeviceId }, { merge: true });
    await batch.commit();
  },
);

/**
 * 새 기기가 세션을 이어 받는다. revokeRefreshTokens 는 기기를 구분하지 않아 방금 로그인한 새 폰도 끊으므로,
 * 커스텀 토큰을 함께 돌려주고 새 폰은 바로 signInWithCustomToken 으로 다시 로그인한다.
 * 챌린지 서명을 새 기기 키로 확인한다 — 같은 uid 의 옛 폰이 이 함수를 먼저 불러 토큰을 받아 가지 못하게.
 */
export const finalizeDevice = onCall({ region: REGION }, async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', '로그인이 필요하다');
  const { coupleId, rotationId, challengeSig } = req.data as { coupleId: string; rotationId: string; challengeSig: string };
  const rotation = (await db.doc(`couples/${coupleId}/rotations/${rotationId}`).get()).data();
  if (!rotation || rotation.uid !== uid) throw new HttpsError('permission-denied', '내 교체 요청이 아니다');
  const device = await db.doc(`couples/${coupleId}/devices/${rotation.newDeviceId}`).get();
  if (!device.get('active')) throw new HttpsError('failed-precondition', '상대 폰이 아직 확인하지 않았다');
  if (!verifyEd25519(finalizeMessage(coupleId, rotationId, uid), b64(challengeSig), b64(device.get('signPub')))) {
    throw new HttpsError('permission-denied', '새 기기 서명이 맞지 않는다');
  }
  const token = await getAuth().createCustomToken(uid);
  await getAuth().revokeRefreshTokens(uid);
  return { token };
});

// ── 상대 폰이 넘겨주기(7-3-B) ─────────────────────────────────────────────

/**
 * 지수(활성 기기)의 confirm-inviter 와 새 폰의 confirm-joiner 가 모두 있고 SAS 가 같으면:
 * 지수 기기 서명을 확인 → 새 기기 등록 · 활성화, 옛 기기 폐기(cutoff), members 갱신, 새 커플 키 봉투.
 */
export const onRelinkStep = onDocumentCreated({ document: 'relinks/{code}/steps/{name}', region: REGION }, async (event) => {
  const { code, name } = event.params;
  if (!name.startsWith('confirm-')) return;
  const base = db.doc(`relinks/${code}`);
  const [relink, join, inviter, reveal, ci, cj] = await Promise.all(
    ['', '/steps/join', '/steps/inviter', '/steps/reveal', '/steps/confirm-inviter', '/steps/confirm-joiner'].map((p) => db.doc(`relinks/${code}${p}`).get()),
  );
  if (![relink, join, inviter, reveal, ci, cj].every((s) => s.exists)) return;
  if (ci.get('sas') !== cj.get('sas')) {
    await db.recursiveDelete(base);
    return;
  }
  const coupleId = relink.get('coupleId') as string;
  const giverDevice = await db.doc(`couples/${coupleId}/devices/${inviter.get('deviceId')}`).get();
  if (!giverDevice.get('active') || giverDevice.get('uid') !== relink.get('uid')) return;
  const cutoff = JSON.parse(ci.get('cutoff') as string) as Record<string, number>;
  const newDeviceId = ci.get('newDeviceId') as string;
  if (newDeviceId !== reveal.get('deviceId')) return;
  if (!verifyEd25519(approvalMessage(coupleId, code, newDeviceId, cutoff), b64(ci.get('sig')), b64(giverDevice.get('signPub')))) return;

  const receiverUid = join.get('uid') as string;
  const couple = await coupleOf(coupleId);
  const oldDevice = couple.members[receiverUid];
  const batch = db.batch();
  batch.set(db.doc(`couples/${coupleId}/devices/${newDeviceId}`), {
    uid: receiverUid, signPub: reveal.get('signPub'), boxPub: reveal.get('boxPub'), state: 'active', active: true, createdAt: FieldValue.serverTimestamp(),
  });
  if (oldDevice && oldDevice !== newDeviceId) {
    batch.update(db.doc(`couples/${coupleId}/devices/${oldDevice}`), { state: 'revoked', active: false, revokedAt: FieldValue.serverTimestamp(), cutoff });
  }
  batch.update(db.doc(`couples/${coupleId}`), { [`members.${receiverUid}`]: newDeviceId });
  batch.set(db.doc(`couples/${coupleId}/keys/${newDeviceId}`), { wrapped: ci.get('wrappedKey'), epoch: Number(ci.get('epoch')) });
  batch.set(db.doc(`users/${receiverUid}`), { coupleId, deviceId: newDeviceId }, { merge: true });
  await batch.commit();
  await db.recursiveDelete(base);
  await push(receiverUid, { data: { kind: 'relinked', coupleId } });
});

// ── iOS 위젯 전송 ─────────────────────────────────────────────────────────

/**
 * 위젯 프로세스에는 Firebase SDK 가 없다. 기기 서명 · opId 재사용 거부 · 기기별 횟수 제한으로 받는다.
 * App Check 를 위젯에서 쓸 수 있는지는 0단계 PoC 결과에 따라 추가한다.
 */
export const sendFromWidget = onRequest({ region: REGION }, async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).end();
    return;
  }
  const { coupleId, fromDeviceId, toDeviceId, opId, kind, header, ct, sig } = req.body ?? {};
  if (![coupleId, fromDeviceId, toDeviceId, opId, kind, header, ct, sig].every((v) => typeof v === 'string') || ct.length > 4000 || kind !== 'missyou') {
    res.status(400).json({ error: 'bad-request' });
    return;
  }
  const device = await db.doc(`couples/${coupleId}/devices/${fromDeviceId}`).get();
  if (!device.get('active') || !verifyEd25519(opSignedBytes(b64(header), b64(ct)), b64(sig), b64(device.get('signPub')))) {
    res.status(403).json({ error: 'signature' });
    return;
  }
  const minute = Math.floor(Date.now() / 60000);
  const day = Math.floor(Date.now() / 86400000);
  const rateRef = db.doc(`widgetRate/${fromDeviceId}`);
  const accepted = await db.runTransaction(async (tx) => {
    const seen = await tx.get(db.doc(`widgetOps/${opId}`));
    if (seen.exists) return 'replay';
    const rate = (await tx.get(rateRef)).data() ?? {};
    const perMin = rate.minute === minute ? rate.perMin + 1 : 1;
    const perDay = rate.day === day ? rate.perDay + 1 : 1;
    if (perMin > 10 || perDay > 200) return 'rate';
    tx.set(rateRef, { minute, perMin, day, perDay });
    tx.set(db.doc(`widgetOps/${opId}`), { at: FieldValue.serverTimestamp(), expireAt: Timestamp.fromMillis(Date.now() + 7 * 86400000) });
    tx.set(db.doc(`couples/${coupleId}/inbox/${toDeviceId}/ops/${opId}`), {
      kind, from: fromDeviceId, createdAt: FieldValue.serverTimestamp(),
      expireAt: Timestamp.fromMillis(Date.now() + 31 * 86400000), size: ct.length, ct, sig, header,
    });
    return 'ok';
  });
  res.status(accepted === 'ok' ? 200 : accepted === 'replay' ? 409 : 429).json({ result: accepted });
});

// ── 로그인 ───────────────────────────────────────────────────────────────

/** 카카오 액세스 토큰 → 커스텀 토큰. app_id 를 확인해 다른 카카오 앱에서 받은 토큰을 거부한다. */
export const kakaoSignIn = onCall({ region: REGION }, async (req) => {
  const accessToken = (req.data as { accessToken?: string }).accessToken;
  if (!accessToken) throw new HttpsError('invalid-argument', '토큰이 없다');
  const r = await fetch('https://kapi.kakao.com/v1/user/access_token_info', { headers: { Authorization: `Bearer ${accessToken}` } });
  if (!r.ok) throw new HttpsError('unauthenticated', '카카오 토큰이 유효하지 않다');
  const info = (await r.json()) as { id: number; app_id: number; expires_in: number };
  if (String(info.app_id) !== KAKAO_APP_ID.value()) throw new HttpsError('permission-denied', '다른 앱에서 발급된 토큰이다');
  if (info.expires_in <= 0) throw new HttpsError('unauthenticated', '만료된 토큰이다');
  return { token: await getAuth().createCustomToken(`kakao:${info.id}`) };
});

// ── 연결 해제 · 탈퇴 ──────────────────────────────────────────────────────

async function deleteCouple(coupleId: string, members: Record<string, string>) {
  await db.recursiveDelete(db.doc(`couples/${coupleId}`));
  await getStorage().bucket().deleteFiles({ prefix: `couples/${coupleId}/` }).catch(() => undefined);
  await Promise.all(Object.keys(members).map(async (u) => {
    await db.doc(`notifyState/${u}`).delete();
    await db.doc(`users/${u}`).set({ coupleId: FieldValue.delete(), deviceId: FieldValue.delete() }, { merge: true });
  }));
}

/** 둘 중 누구나 혼자 해제할 수 있다. 서버 데이터는 즉시 지우고 상대에게 한 번 알린다. */
export const unlinkCouple = onCall({ region: REGION }, async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', '로그인이 필요하다');
  const coupleId = (req.data as { coupleId: string }).coupleId;
  const couple = await coupleOf(coupleId);
  if (!(uid in couple.members)) throw new HttpsError('permission-denied', '멤버가 아니다');
  await deleteCouple(coupleId, couple.members);
  const partner = Object.keys(couple.members).find((u) => u !== uid);
  if (partner) await push(partner, { visible: { title: '꽃신', body: '연결이 해제됐어요' }, data: { kind: 'unlinked', coupleId } });
  return { ok: true };
});

/**
 * 탈퇴. 커스텀 토큰으로 다시 로그인한 기기는 재인증을 할 수 없으므로 Admin SDK 로 처리한다.
 * 카카오 연결 끊기, Apple 토큰 폐기(Apple 로그인 앱 필수)도 여기서 한다.
 */
export const deleteAccount = onCall({ region: REGION }, async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', '로그인이 필요하다');
  const user = await db.doc(`users/${uid}`).get();
  const coupleId = user.get('coupleId') as string | undefined;
  if (coupleId) {
    const couple = await coupleOf(coupleId).catch(() => null);
    if (couple) await deleteCouple(coupleId, couple.members);
  }
  await getStorage().bucket().deleteFiles({ prefix: `backups/${uid}/` }).catch(() => undefined);
  if (uid.startsWith('kakao:') && KAKAO_ADMIN_KEY.value()) {
    await fetch('https://kapi.kakao.com/v1/user/unlink', {
      method: 'POST',
      headers: { Authorization: `KakaoAK ${KAKAO_ADMIN_KEY.value()}`, 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `target_id_type=user_id&target_id=${uid.slice('kakao:'.length)}`,
    }).catch(() => undefined);
  }
  // Apple: 로그인 때 보관한 refresh token 을 폐기한다(App Store 심사 기준 5.1.1(v)).
  const apple = await db.doc(`appleTokens/${uid}`).get();
  const refreshToken = apple.get('refreshToken') as string | undefined;
  if (refreshToken && appleConfigured(appleConfig())) await revokeApple(appleConfig(), refreshToken).catch(() => false);
  await apple.ref.delete();
  await db.recursiveDelete(db.doc(`users/${uid}`));
  await getAuth().deleteUser(uid);
  return { ok: true };
});

/**
 * 문의 답변 알림(Figma 7 · 문의 내역 "답변이 오면 알림"). 답변은 콘솔 · 관리자 SDK 로 answer 를 쓴다.
 * answeredAt 이 없으면 채운다 — 그 쓰기로 다시 불려도 before 에 answer 가 있어 알림은 한 번만 간다.
 */
export const onInquiryAnswered = onDocumentWritten({ document: 'users/{uid}/inquiries/{id}', region: REGION }, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!after || typeof after.answer !== 'string' || after.answer.length === 0) return;
  if (before && typeof before.answer === 'string' && before.answer.length > 0) return;
  if (!after.answeredAt) await event.data!.after.ref.update({ answeredAt: FieldValue.serverTimestamp(), status: 'answered' });
  await push(event.params.uid, {
    visible: { title: '꽃신', body: '문의에 답변이 왔어요' },
    data: { kind: 'inquiry', id: event.params.id },
  });
});

/**
 * Sign in with Apple 직후 iOS 가 부른다: 일회용 authorization code → refresh token 을 appleTokens/{uid} 에 보관.
 * 이 컬렉션은 규칙에 없어 클라이언트가 읽고 쓸 수 없다(관리자 SDK 만).
 */
export const appleStoreToken = onCall({ region: REGION }, async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', '로그인이 필요하다');
  if (req.auth?.token.firebase?.sign_in_provider !== 'apple.com') throw new HttpsError('failed-precondition', 'Apple 로그인이 아니다');
  const code = (req.data as { code?: unknown })?.code;
  if (typeof code !== 'string' || code.length === 0 || code.length > 1024) throw new HttpsError('invalid-argument', 'code 가 없다');
  if (!appleConfigured(appleConfig())) return { stored: false };
  const refreshToken = await exchangeCode(appleConfig(), code);
  if (!refreshToken) throw new HttpsError('permission-denied', 'Apple 이 code 를 거절했다');
  await db.doc(`appleTokens/${uid}`).set({ refreshToken, storedAt: FieldValue.serverTimestamp() });
  return { stored: true };
});


/**
 * 백업 보관 기간(약관 제10조의2): 매일 새벽, 마지막 사용(users/{uid}.lastSeenAt) 이 오래된 사람에게 30일 · 7일 전 예고를 보내고
 * 기한이 지나면 backups/{uid}/ 를 지운다. 꽃신 평생(entitlements/{uid}.lifetime, RevenueCat 웹훅이 쓴다)은 10년.
 * 기기 안의 기록은 지우지 않는다. 이메일 예고는 메일 발송을 붙이면서 함께 보낸다.
 */
export const backupRetention = onSchedule({ schedule: 'every day 04:00', region: REGION, timeZone: 'Asia/Seoul' }, async () => {
  const now = Date.now();
  const cutoff = Timestamp.fromMillis(now - (FREE_KEEP_DAYS - Math.max(...WARN_DAYS)) * DAY_MS);
  const users = await db.collection('users').where('lastSeenAt', '<=', cutoff).get();
  for (const user of users.docs) {
    const uid = user.id;
    if (user.get('backupDeletedAt')) continue;
    const lastSeen = (user.get('lastSeenAt') as Timestamp).toMillis();
    const ent = await db.doc(`entitlements/${uid}`).get();
    const lifetime = ent.get('lifetime') === true;
    // 예고 기록은 같은 lastSeenAt 동안만 유효하다(앱을 쓰면 새로 센다).
    const warnedFor = user.get('retentionWarnedFor') as number | undefined;
    const warned = warnedFor === lastSeen ? ((user.get('retentionWarned') as number[] | undefined) ?? []) : [];
    const action = retentionAction(lastSeen, lifetime, now, warned);
    if (action.type === 'warn') {
      const msg = warnMessage(action.daysLeft);
      await push(uid, { visible: msg, data: { kind: 'backupExpiry', daysLeft: String(action.daysLeft) } });
      // 이메일: 로그인 제공자가 이메일을 준 경우만. "Trigger Email" 확장이 mail 문서를 보낸다(확장이 없으면 쌓이기만 한다).
      const email = await getAuth().getUser(uid).then((u) => u.email).catch(() => undefined);
      if (email) await db.collection('mail').add({ to: email, message: { subject: msg.title, text: msg.body } });
      await user.ref.set({ retentionWarnedFor: lastSeen, retentionWarned: [...warned, action.daysLeft] }, { merge: true });
    } else if (action.type === 'delete') {
      await getStorage().bucket().deleteFiles({ prefix: `backups/${uid}/` }).catch(() => undefined);
      await user.ref.set({ backupDeletedAt: FieldValue.serverTimestamp() }, { merge: true });
    }
  }
});

/**
 * RevenueCat 웹훅(대시보드 › Integrations › Webhooks, Authorization 헤더 = REVENUECAT_WEBHOOK_AUTH).
 * 알림이 온 사용자의 이용권을 REST API 로 다시 읽어 entitlements/{uid} 에 쓴다 — backupRetention 이 평생 10년을 여기서 본다.
 */
export const revenuecatWebhook = onRequest({ region: REGION, secrets: [REVENUECAT_WEBHOOK_AUTH, REVENUECAT_SECRET_KEY] }, async (req, res) => {
  const auth = REVENUECAT_WEBHOOK_AUTH.value();
  if (!auth || req.get('authorization') !== auth) {
    res.status(401).send('unauthorized');
    return;
  }
  const key = REVENUECAT_SECRET_KEY.value();
  if (!key) {
    res.status(200).send('no key');
    return;
  }
  for (const uid of usersOf(req.body)) {
    const r = await fetch(`https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(uid)}`, { headers: { Authorization: `Bearer ${key}` } });
    if (!r.ok) continue;
    const ent = entitlementsFrom(await r.json(), new Date());
    await db.doc(`entitlements/${uid}`).set({ ...ent, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  }
  res.status(200).send('ok');
});
