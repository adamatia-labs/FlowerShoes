// Firestore 보안 규칙 테스트(설계 7-2). 에뮬레이터 위에서 실제 규칙으로 허용 · 거부를 확인한다.
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs, serverTimestamp, Timestamp } from 'firebase/firestore';

let env;
const KAKAO = { firebase: { sign_in_provider: 'custom' } };

before(async () => {
  env = await initializeTestEnvironment({
    projectId: process.env.GCLOUD_PROJECT ?? 'demo-kkotsin',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

const as = (uid) => env.authenticatedContext(uid, KAKAO).firestore();
const later = () => Timestamp.fromMillis(Date.now() + 60 * 60 * 1000);

test('메일 발송 요청·수신자·상태는 로그인 사용자와 비로그인 사용자 모두 접근할 수 없다', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'mail/test-mail'), { to: 'private@example.test', message: { subject: 'x', text: 'x' } });
  });
  for (const db of [as('jisu'), env.unauthenticatedContext().firestore()]) {
    await assertFails(getDoc(doc(db, 'mail/test-mail')));
    await assertFails(getDocs(collection(db, 'mail')));
    await assertFails(setDoc(doc(db, 'mail/new-mail'), { to: 'spam@example.test' }));
    await assertFails(setDoc(doc(db, 'mail/test-mail'), { delivery: { state: 'SUCCESS' } }, { merge: true }));
    await assertFails(deleteDoc(doc(db, 'mail/test-mail')));
  }
});

/** 관리자 권한으로 커플 하나를 깔아 둔다: jisu(dev-a) · minjun(dev-b) 둘 다 활성. */
async function seedCouple() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'couples/c1'), { members: { jisu: 'dev-a', minjun: 'dev-b' } });
    await setDoc(doc(db, 'couples/c1/devices/dev-a'), { uid: 'jisu', active: true, state: 'active' });
    await setDoc(doc(db, 'couples/c1/devices/dev-b'), { uid: 'minjun', active: true, state: 'active' });
  });
}

const op = (from) => ({ kind: 'missyou', from, createdAt: Timestamp.now(), expireAt: later(), size: 10, ct: 'AAAA', sig: 'BBBB', header: 'h' });

// ── 첫 연결: 초대 링크 ───────────────────────────────────────────────────

const TOKEN = 'lMUx7lUYw_FL1C-F1P9pVA';
const PUB = 'A'.repeat(88);
const invite = (uid, extra = {}) => ({ uid, deviceId: 'dev-a', pubKey: PUB, name: '지수', expireAt: later(), ...extra });
const accept = (uid) => ({ uid, deviceId: 'dev-b', pubKey: PUB });
const days = (n) => Timestamp.fromMillis(Date.now() + n * 86400 * 1000);

test('초대 문서는 uid · deviceId · pubKey · 이름 · 7일 이하 만료만 담는다', async () => {
  await assertSucceeds(setDoc(doc(as('jisu'), `pairings/${TOKEN}`), invite('jisu', { expireAt: days(7) })));
  await assertFails(setDoc(doc(as('jisu'), 'pairings/BBBBBBBBBBBBBBBBBBBBBB'), invite('jisu', { expireAt: days(8) })));
  await assertFails(setDoc(doc(as('jisu'), 'pairings/CCCCCCCCCCCCCCCCCCCCCC'), invite('jisu', { phone: '010' })));
  await assertFails(setDoc(doc(as('jisu'), 'pairings/DDDDDDDDDDDDDDDDDDDDDD'), invite('minjun')));
  await assertFails(setDoc(doc(as('jisu'), 'pairings/EEEEEEEEEEEEEEEEEEEEEE'), invite('jisu', { name: '가'.repeat(41) })));
});

test('토큰은 128비트(base64url 22자)여야 한다', async () => {
  await assertFails(setDoc(doc(as('jisu'), 'pairings/K7M2Q9XA'), invite('jisu')));
});

test('초대는 토큰을 아는 사람만 읽고 목록은 아무도 못 본다', async () => {
  await setDoc(doc(as('jisu'), `pairings/${TOKEN}`), invite('jisu'));
  await assertSucceeds(getDoc(doc(as('minjun'), `pairings/${TOKEN}`)));
  await assertFails(getDocs(collection(as('minjun'), 'pairings')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `pairings/${TOKEN}`)));
});

test('수락은 받는 사람 본인이 한 번만 쓴다', async () => {
  await setDoc(doc(as('jisu'), `pairings/${TOKEN}`), invite('jisu'));
  // 보낸 본인은 자기 링크를 수락할 수 없다.
  await assertFails(setDoc(doc(as('jisu'), `pairings/${TOKEN}/steps/accept`), accept('jisu')));
  // 다른 사람인 척 할 수 없다.
  await assertFails(setDoc(doc(as('mallory'), `pairings/${TOKEN}/steps/accept`), accept('minjun')));
  // accept 말고 다른 단계는 없다.
  await assertFails(setDoc(doc(as('minjun'), `pairings/${TOKEN}/steps/join`), accept('minjun')));
  await assertSucceeds(setDoc(doc(as('minjun'), `pairings/${TOKEN}/steps/accept`), accept('minjun')));
  // 두 번째 수락(다른 사람이든 같은 사람이든)은 막힌다.
  await assertFails(setDoc(doc(as('mallory'), `pairings/${TOKEN}/steps/accept`), accept('mallory')));
  await assertFails(setDoc(doc(as('minjun'), `pairings/${TOKEN}/steps/accept`), { ...accept('minjun'), pubKey: 'B'.repeat(88) }));
});

test('만료된 초대 · 없는 초대는 수락할 수 없다', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `pairings/${TOKEN}`), invite('jisu', { expireAt: Timestamp.fromMillis(Date.now() - 1000) }));
  });
  await assertFails(setDoc(doc(as('minjun'), `pairings/${TOKEN}/steps/accept`), accept('minjun')));
  await assertFails(setDoc(doc(as('minjun'), 'pairings/ZZZZZZZZZZZZZZZZZZZZZZ/steps/accept'), accept('minjun')));
});

test('커플 연결 표시(coupleId · deviceId · linkedVia)는 본인도 쓰지 못한다', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/minjun'), { coupleId: 'c1', deviceId: 'dev-b', linkedVia: TOKEN });
  });
  await assertSucceeds(setDoc(doc(as('minjun'), 'users/minjun'), { fcmTokens: ['t'] }, { merge: true }));
  await assertFails(setDoc(doc(as('minjun'), 'users/minjun'), { coupleId: 'c2' }, { merge: true }));
  await assertFails(setDoc(doc(as('minjun'), 'users/minjun'), { fcmTokens: ['t'] })); // 덮어쓰면 coupleId 가 지워진다
  await assertFails(setDoc(doc(as('jisu'), 'users/jisu'), { linkedVia: TOKEN }));
  await assertSucceeds(setDoc(doc(as('jisu'), 'users/jisu'), { fcmTokens: ['t'] }));
});

test('익명 로그인은 아무것도 못 한다', async () => {
  const anon = env.authenticatedContext('anon', { firebase: { sign_in_provider: 'anonymous' } }).firestore();
  await assertFails(setDoc(doc(anon, 'pairings/YYYYYYYYYYYYYYYYYYYYYY'), invite('anon')));
});

// ── 우편함 · 기기 ────────────────────────────────────────────────────────

test('우편함은 상대 기기 앞으로만, 내 기기 id 로만 쓴다', async () => {
  await seedCouple();
  await assertSucceeds(setDoc(doc(as('jisu'), 'couples/c1/inbox/dev-b/ops/op1'), op('dev-a')));
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/inbox/dev-b/ops/op2'), op('dev-b'))); // 남의 기기인 척
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/inbox/dev-a/ops/op3'), op('dev-a'))); // 내 우편함에 쓰기
  await assertFails(setDoc(doc(as('mallory'), 'couples/c1/inbox/dev-b/ops/op4'), op('dev-a'))); // 커플 밖
});

test('우편함은 그 기기에 묶인 사람만 읽고 지운다', async () => {
  await seedCouple();
  await setDoc(doc(as('jisu'), 'couples/c1/inbox/dev-b/ops/op1'), op('dev-a'));
  await assertFails(getDoc(doc(as('jisu'), 'couples/c1/inbox/dev-b/ops/op1')));
  await assertSucceeds(getDoc(doc(as('minjun'), 'couples/c1/inbox/dev-b/ops/op1')));
  await assertSucceeds(deleteDoc(doc(as('minjun'), 'couples/c1/inbox/dev-b/ops/op1')));
});

test('폐기된 기기는 우편함에 쓸 수 없다', async () => {
  await seedCouple();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'couples/c1/devices/dev-a'), { uid: 'jisu', active: false, state: 'revoked' });
  });
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/inbox/dev-b/ops/op1'), op('dev-a')));
});

test('새 기기는 대기 상태로만 등록하고 스스로 활성화할 수 없다', async () => {
  await seedCouple();
  const pending = { uid: 'minjun', signPub: 's', boxPub: 'b', state: 'pending', active: false, createdAt: Timestamp.now() };
  await assertSucceeds(setDoc(doc(as('minjun'), 'couples/c1/devices/dev-b2'), pending));
  await assertFails(setDoc(doc(as('minjun'), 'couples/c1/devices/dev-b3'), { ...pending, state: 'active', active: true }));
  await assertFails(setDoc(doc(as('minjun'), 'couples/c1/devices/dev-b2'), { ...pending, active: true }));
  await assertFails(setDoc(doc(as('minjun'), 'couples/c1/devices/dev-x'), { ...pending, uid: 'jisu' }));
});

// ── 기기 교체(#20) ───────────────────────────────────────────────────────

const pendingDevice = { uid: 'minjun', signPub: 's', boxPub: 'b', state: 'pending', active: false, createdAt: Timestamp.now() };
const change = (extra = {}) => ({
  uid: 'minjun', newDeviceId: 'dev-b2', oldDeviceId: 'dev-b', deviceName: 'Galaxy S25', provider: 'KAKAO',
  status: 'pending', createdAt: serverTimestamp(), expireAt: days(7), ...extra,
});

test('기기 교체 요청은 같은 계정의 대기 기기가 서버 시각 · 7일 이하로만 만든다', async () => {
  await seedCouple();
  const minjun = as('minjun');
  // 대기 기기 문서가 먼저 있어야 한다.
  await assertFails(setDoc(doc(minjun, 'couples/c1/deviceChanges/r0'), change()));
  await setDoc(doc(minjun, 'couples/c1/devices/dev-b2'), pendingDevice);
  await assertSucceeds(setDoc(doc(minjun, 'couples/c1/deviceChanges/r1'), change()));
  await assertFails(setDoc(doc(minjun, 'couples/c1/deviceChanges/r2'), change({ createdAt: Timestamp.fromMillis(Date.now() - 86400 * 1000) })));
  await assertFails(setDoc(doc(minjun, 'couples/c1/deviceChanges/r3'), change({ expireAt: days(8) })));
  await assertFails(setDoc(doc(minjun, 'couples/c1/deviceChanges/r4'), change({ status: 'approved' })));
  await assertFails(setDoc(doc(minjun, 'couples/c1/deviceChanges/r5'), change({ oldDeviceId: 'dev-a' })));
  await assertFails(setDoc(doc(minjun, 'couples/c1/deviceChanges/r6'), change({ second: false })));
  // 상대 계정 이름으로 · 커플 밖에서는 못 만든다.
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/deviceChanges/r7'), change({ uid: 'jisu' })));
  await assertFails(setDoc(doc(as('mallory'), 'couples/c1/deviceChanges/r8'), change({ uid: 'mallory' })));
  // 상태는 함수만 바꾼다.
  await assertFails(setDoc(doc(minjun, 'couples/c1/deviceChanges/r1'), { status: 'approved' }, { merge: true }));
  await assertSucceeds(getDocs(collection(as('jisu'), 'couples/c1/deviceChanges')));
});

test('승인 · 거절은 상대 활성 기기만, 취소 · 막기는 요청한 계정만 한 번 답한다', async () => {
  await seedCouple();
  await setDoc(doc(as('minjun'), 'couples/c1/devices/dev-b2'), pendingDevice);
  await setDoc(doc(as('minjun'), 'couples/c1/deviceChanges/r1'), change());
  const approve = { result: 'approve', approverDeviceId: 'dev-a', cutoff: { 'dev-b:app': 10 }, sig: 's', counts: { letters: 3 }, at: serverTimestamp() };
  await assertFails(setDoc(doc(as('minjun'), 'couples/c1/deviceChanges/r1/answer/partner'), approve));
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/deviceChanges/r1/answer/partner'), { ...approve, approverDeviceId: 'dev-b' }));
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/deviceChanges/r1/answer/owner'), { result: 'block', at: serverTimestamp() }));
  await assertFails(setDoc(doc(as('minjun'), 'couples/c1/deviceChanges/r1/answer/owner'), { result: 'approve', at: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(as('minjun'), 'couples/c1/deviceChanges/r1/answer/owner'), { result: 'block', at: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(as('jisu'), 'couples/c1/deviceChanges/r1/answer/partner'), approve));
  // 한 번만.
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/deviceChanges/r1/answer/partner'), { result: 'reject', at: serverTimestamp() }));
  // 승인한 폰은 새 기기 앞으로 커플 키 봉투를 둔다(새 기기는 활성화된 뒤에야 읽는다).
  await assertSucceeds(setDoc(doc(as('jisu'), 'couples/c1/keys/dev-b2'), { wrapped: 'w', epoch: 2 }));
  await assertFails(getDoc(doc(as('minjun'), 'couples/c1/keys/dev-b2')));
});

test('끝났거나 만료된 요청에는 답할 수 없다', async () => {
  await seedCouple();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'couples/c1/deviceChanges/done'), { ...change(), createdAt: Timestamp.now(), status: 'rejected' });
    await setDoc(doc(ctx.firestore(), 'couples/c1/deviceChanges/old'), { ...change(), createdAt: Timestamp.now(), expireAt: Timestamp.fromMillis(Date.now() - 1000) });
  });
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/deviceChanges/done/answer/partner'), { result: 'reject', at: serverTimestamp() }));
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/deviceChanges/old/answer/partner'), { result: 'reject', at: serverTimestamp() }));
});

test('커플 문서는 멤버만 읽고 아무도 직접 만들지 못한다', async () => {
  await seedCouple();
  await assertSucceeds(getDoc(doc(as('jisu'), 'couples/c1')));
  await assertFails(getDoc(doc(as('mallory'), 'couples/c1')));
  await assertFails(setDoc(doc(as('mallory'), 'couples/c2'), { members: { mallory: 'x', jisu: 'dev-a' } }));
});

test('알림 설정은 본인만, 서버 상태 문서는 아무도', async () => {
  await assertSucceeds(setDoc(doc(as('jisu'), 'users/jisu/notifyPrefs/main'), { quietStart: 1380, quietEnd: 420, tz: 'Asia/Seoul' }));
  await assertFails(setDoc(doc(as('minjun'), 'users/jisu/notifyPrefs/main'), { quietStart: 0, quietEnd: 0 }));
  await assertFails(getDoc(doc(as('jisu'), 'notifyState/jisu')));
});

test('첨부 blob 은 활성 멤버만 올리고 멤버만 읽는다', async () => {
  await seedCouple();
  const blob = { data: 'QUJD', expireAt: later() };
  await assertSucceeds(setDoc(doc(as('jisu'), 'couples/c1/blobs/b1'), blob));
  await assertSucceeds(getDoc(doc(as('minjun'), 'couples/c1/blobs/b1')));
  await assertFails(getDoc(doc(as('mallory'), 'couples/c1/blobs/b1')));
  await assertFails(setDoc(doc(as('mallory'), 'couples/c1/blobs/b2'), blob));
  await assertFails(setDoc(doc(as('jisu'), 'couples/c1/blobs/b3'), { ...blob, extra: 1 }));
});

test('Apple refresh token 보관함은 본인도 읽고 쓸 수 없다(함수만)', async () => {
  await assertFails(getDoc(doc(as('jisu'), 'appleTokens/jisu')));
  await assertFails(setDoc(doc(as('jisu'), 'appleTokens/jisu'), { refreshToken: 'x' }));
  // 이용권(RevenueCat 웹훅이 쓴다) · 메일 대기열은 서버만.
  await assertFails(getDoc(doc(as('jisu'), 'entitlements/jisu')));
  await assertFails(setDoc(doc(as('jisu'), 'entitlements/jisu'), { lifetime: true }));
  await assertFails(setDoc(doc(as('jisu'), 'mail/x'), { to: 'a@b.c' }));
});

