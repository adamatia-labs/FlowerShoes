import { after, before, mock, test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomUUID } from 'node:crypto';
import { deleteApp, getApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, QueryDocumentSnapshot, Timestamp } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';
import { claimAccountDeletion, completeAccountDeletion, deletionPhase, deletionRef, rememberAppleRevocation } from '../account-lifecycle';

const PROJECT = 'demo-kkotsin-account';
if (process.env.GCLOUD_PROJECT !== PROJECT || !process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST || !process.env.STORAGE_EMULATOR_HOST) throw new Error('Local demo Auth/Firestore/Storage emulators required');
let functions: typeof import('../index');
const realFetch = globalThis.fetch;

before(async () => {
  process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: PROJECT, storageBucket: `${PROJECT}.appspot.com` });
  process.env.KAKAO_APP_ID = '42';
  process.env.KAKAO_ADMIN_KEY = '';
  process.env.APPLE_TEAM_ID = 'TESTTEAM';
  process.env.APPLE_KEY_ID = 'TESTKEY';
  process.env.APPLE_CLIENT_ID = 'test.example.app';
  process.env.APPLE_PRIVATE_KEY = generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  functions = await import('../index');
});
after(async () => { mock.restoreAll(); await getFirestore().terminate(); await deleteApp(getApp()); });

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => { resolve = done; });
  return { promise, resolve };
}

function mockKakao(id: number) {
  return mock.method(globalThis, 'fetch', (async (url: string | URL | Request, init?: RequestInit) => {
    if (String(url).endsWith('/access_token_info')) return Response.json({ id, app_id: 42, expires_in: 3600 });
    if (String(url).endsWith('/v2/user/me')) return Response.json({});
    return realFetch(url, init);
  }) as typeof fetch);
}

async function kakao() {
  return functions.kakaoSignIn.run({ data: { accessToken: 'local-test' }, rawRequest: {} } as never);
}
async function remove(uid: string, appleSubject?: string) {
  return functions.deleteAccount.run({ data: {}, auth: { uid, token: { firebase: {
    sign_in_provider: 'custom', ...(appleSubject ? { identities: { 'apple.com': [appleSubject] } } : {}),
  } } }, rawRequest: {} } as never);
}

function receipt(ownerUid: string, receiptToken: string) {
  return functions.accountDeletionResult.run({ data: { ownerUid, receiptToken }, rawRequest: {} } as never);
}

test('인증 삭제와 응답 유실 뒤에도 원래 작업 비밀로만 완료 결과를 조회한다', async () => {
  const uid = 'receipt-lost-response';
  const token = randomUUID();
  await getAuth().createUser({ uid });
  await getFirestore().doc(`users/${uid}`).set({ nickname: 'local-test' });
  assert.deepEqual(await receipt(uid, token), { completed: false });
  await functions.deleteAccount.run({ data: { expectedUid: uid, receiptToken: token },
    auth: { uid, token: { firebase: { sign_in_provider: 'apple.com' } } }, rawRequest: {} } as never);
  await assert.rejects(getAuth().getUser(uid), { code: 'auth/user-not-found' });
  assert.deepEqual(await receipt(uid, token), { completed: true, appleRevocation: 'manual' });
  assert.deepEqual(await receipt(uid, randomUUID()), { completed: false });
  assert.deepEqual(await receipt('another-account', token), { completed: false });
  const stored = (await deletionRef(uid).get()).data()!;
  assert.equal(JSON.stringify(stored).includes(token), false);
  assert.equal(typeof stored.receiptHash, 'string');
});

test('최종 완료 저장 전에 중단되면 Auth 부재를 확인하고 영수증만 확정한다', async () => {
  const uid = 'receipt-final-write';
  const token = randomUUID();
  await getAuth().createUser({ uid });
  const claim = await claimAccountDeletion(uid, token);
  assert.ok(claim.attempt);
  await rememberAppleRevocation(uid, claim.attempt, 'manual');
  assert.deepEqual(await receipt(uid, token), { completed: false });
  await deletionPhase(uid, claim.attempt, 'auth-delete');
  assert.deepEqual(await receipt(uid, token), { completed: false });
  await getAuth().deleteUser(uid);
  assert.deepEqual(await receipt(uid, token), { completed: true, appleRevocation: 'manual' });
  // The original executor can finish after the receipt reader without undoing its result.
  await completeAccountDeletion(uid, claim.attempt, { ok: true, appleRevocation: 'manual' });
  assert.deepEqual(await receipt(uid, token), { completed: true, appleRevocation: 'manual' });
});

test('결과 조회는 미완료 제공자 정리나 다른 계정의 삭제를 시작하지 않는다', async () => {
  const uid = 'receipt-unauthorized';
  const token = randomUUID();
  await getAuth().createUser({ uid });
  await assert.rejects(functions.deleteAccount.run({ data: { expectedUid: 'previous-owner', receiptToken: token },
    auth: { uid, token: {} }, rawRequest: {} } as never), /계정이 바뀌/);
  assert.equal((await deletionRef(uid).get()).exists, false);
  await claimAccountDeletion(uid, token);
  await getAuth().deleteUser(uid);
  assert.deepEqual(await receipt(uid, token), { completed: false });
  assert.equal((await deletionRef(uid).get()).get('state'), 'deleting');
});
async function signIn(customToken: string) {
  const response = await realFetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=local-test`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token: customToken, returnSecureToken: true }),
  });
  assert.equal(response.status, 200, await response.clone().text());
  const data = await response.json() as { idToken: string };
  const decoded = await getAuth().verifyIdToken(data.idToken);
  return { localId: decoded.uid, idToken: data.idToken };
}
async function firestoreRequest(path: string, token: string, write = false) {
  return realFetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/${PROJECT}/databases/(default)/documents/${path}`, {
    method: write ? 'PATCH' : 'GET', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    ...(write ? { body: JSON.stringify({ fields: { nickname: { stringValue: 'local-test' } } }) } : {}),
  });
}

test('Auth 삭제 중 재가입과 중복 탈퇴를 막고, 발급된 옛 토큰이 새 계정에 접근하지 못한다', async () => {
  const request = mockKakao(1001);
  const oldToken = (await kakao()).token;
  const old = await signIn(oldToken);
  assert.equal(old.localId, 'kakao:1001');
  await getFirestore().doc(`users/${old.localId}`).set({ nickname: 'old' });
  const deleted = deferred();
  const finish = deferred();
  const auth = getAuth();
  const deleteUser = auth.deleteUser.bind(auth);
  const paused = mock.method(auth, 'deleteUser', async (uid: string) => {
    await deleteUser(uid);
    deleted.resolve();
    await finish.promise;
  });
  const deleting = remove(old.localId);
  try {
    await deleted.promise;
    await assert.rejects(kakao(), /탈퇴 처리/);
    await assert.rejects(remove(old.localId), /탈퇴 처리/);
    // Already issued custom tokens really recreate an Auth user. The server tombstone must still deny its data access.
    const zombie = await signIn(oldToken);
    assert.equal(zombie.localId, old.localId);
    assert.equal((await firestoreRequest(`users/${old.localId}`, zombie.idToken, true)).status, 403);
    finish.resolve();
    await deleting;
    const fresh = await signIn((await kakao()).token);
    assert.notEqual(fresh.localId, old.localId);
    assert.equal((await firestoreRequest(`users/${fresh.localId}`, fresh.idToken, true)).status, 200);
    assert.equal((await firestoreRequest(`users/${fresh.localId}`, zombie.idToken)).status, 403);
    await remove(old.localId); // a delayed old request must return its cached outcome, not delete a replacement account
    assert.equal((await auth.getUser(fresh.localId)).uid, fresh.localId);
    assert.equal((await getFirestore().doc(`users/${fresh.localId}`).get()).exists, true);
    assert.equal(paused.mock.callCount(), 1);
    for (const path of [`accountDeletions/${old.localId}`, 'kakaoAccounts/1001', `inviteHeads/${fresh.localId}`]) {
      assert.equal((await firestoreRequest(path, fresh.idToken)).status, 403);
      assert.equal((await firestoreRequest(path, fresh.idToken, true)).status, 403);
    }
  } finally { finish.resolve(); await deleting.catch(() => {}); paused.mock.restore(); request.mock.restore(); }
});

test('확정된 정리 실패는 동일 UID로 재시도하고, 완료 전에는 로그인 세대를 바꾸지 않는다', async () => {
  const request = mockKakao(1002);
  const old = await signIn((await kakao()).token);
  const failure = mock.method(getAuth(), 'deleteUser', async () => { throw new Error('injected-before-delete'); });
  try { await assert.rejects(remove(old.localId), /injected-before-delete/); } finally { failure.mock.restore(); }
  try {
    assert.equal((await deletionRef(old.localId).get()).get('state'), 'retryable');
    await assert.rejects(kakao(), /탈퇴 처리/);
    await remove(old.localId);
    assert.equal((await deletionRef(old.localId).get()).get('state'), 'deleted');
    const next = await signIn((await kakao()).token);
    assert.notEqual(next.localId, old.localId);
  } finally { request.mock.restore(); }
});

test('Storage는 이전 UID의 읽기·쓰기를 차단하고 활성 구성원의 접근을 보존한다', async () => {
  const auth = getAuth();
  const old = await signIn(await auth.createCustomToken('storage-old'));
  const live = await signIn(await auth.createCustomToken('storage-live'));
  await getFirestore().doc('couples/storage-fence').set({ members: { [old.localId]: 'old-device', [live.localId]: 'live-device' } });
  const bucket = getStorage().bucket();
  const object = 'couples/storage-fence/existing';
  await bucket.file(object).save('local-test');
  const storageHost = process.env.STORAGE_EMULATOR_HOST!;
  const endpoint = `${storageHost.startsWith('http') ? storageHost : `http://${storageHost}`}/v0/b/${bucket.name}/o`;
  const read = (token: string) => realFetch(`${endpoint}/${encodeURIComponent(object)}?alt=media`, { headers: { Authorization: `Bearer ${token}` } });
  const write = (token: string, name: string) => realFetch(`${endpoint}?uploadType=media&name=${encodeURIComponent(`couples/storage-fence/${name}`)}`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/octet-stream' }, body: 'local-test',
  });
  assert.equal((await read(old.idToken)).status, 200);
  await claimAccountDeletion(old.localId);
  assert.equal((await read(old.idToken)).status, 403);
  assert.equal((await write(old.idToken, 'denied')).status, 403);
  assert.equal((await read(live.idToken)).status, 200);
  assert.equal((await write(live.idToken, 'allowed')).status, 200);
  await getFirestore().doc('unlinkOperations/storage-fence').set({ members: [old.localId, live.localId], completed: false });
  assert.equal((await read(live.idToken)).status, 403);
  assert.equal((await write(live.idToken, 'unlink-denied')).status, 403);
});

test('죽은 실행의 claim은 시간이 지나도 자동으로 빼앗거나 재가입을 허용하지 않는다', async () => {
  const request = mockKakao(1003);
  try {
    const old = await signIn((await kakao()).token);
    await claimAccountDeletion(old.localId);
    await deletionRef(old.localId).update({ startedAt: Timestamp.fromMillis(0) });
    await assert.rejects(remove(old.localId), /탈퇴 처리/);
    await assert.rejects(kakao(), /탈퇴 처리/);
    assert.equal((await deletionRef(old.localId).get()).get('state'), 'deleting');
  } finally { request.mock.restore(); }
});

test('탈퇴 전에 시작해 늦게 돌아온 카카오 로그인은 새 UID나 새 계정 토큰을 받지 못한다', async () => {
  const first = mockKakao(1005);
  const old = await signIn((await kakao()).token);
  first.mock.restore();
  const checked = deferred();
  const resume = deferred();
  const delayed = mock.method(globalThis, 'fetch', (async (url: string | URL | Request, init?: RequestInit) => {
    if (String(url).endsWith('/access_token_info')) {
      checked.resolve();
      await resume.promise;
      return Response.json({ id: 1005, app_id: 42, expires_in: 3600 });
    }
    if (String(url).endsWith('/v2/user/me')) return Response.json({});
    return realFetch(url, init);
  }) as typeof fetch);
  const login = kakao();
  const rejected = assert.rejects(login, /탈퇴 전에 시작한 로그인/);
  try {
    await checked.promise;
    await remove(old.localId);
    delayed.mock.restore();
    const freshRequest = mockKakao(1005);
    try {
      const fresh = await signIn((await kakao()).token);
      assert.notEqual(fresh.localId, old.localId);
      resume.resolve();
      await rejected;
    } finally { freshRequest.mock.restore(); }
  } finally { resume.resolve(); await rejected; delayed.mock.restore(); }
});

test('탈퇴한 초대 발신자나 수락자의 지연 이벤트는 사용자나 관계를 재생성하지 않는다', async () => {
  const db = getFirestore();
  for (const deletingSide of ['inviter', 'joiner']) {
    const token = deletingSide === 'inviter' ? 'AAAAAAAAAAAAAAAAAAAAAA' : 'BBBBBBBBBBBBBBBBBBBBBB';
    const uid = `late-${deletingSide}`;
    await getAuth().createUser({ uid });
    await db.doc(`inviteHeads/${uid}`).set({ token, createdAt: Timestamp.now() });
    const inviter = deletingSide === 'inviter' ? uid : 'live-inviter';
    const joiner = deletingSide === 'joiner' ? uid : 'live-joiner';
    const pubKey = Buffer.alloc(64, 1).toString('base64');
    await db.doc(`pairings/${token}`).set({ uid: inviter, deviceId: 'a', pubKey, name: '', expireAt: Timestamp.fromMillis(Date.now() + 60000) });
    const acceptRef = db.doc(`pairings/${token}/steps/accept`);
    await acceptRef.set({ uid: joiner, deviceId: 'b', pubKey });
    const data = await acceptRef.get() as QueryDocumentSnapshot;
    await remove(uid);
    assert.equal((await db.doc(`inviteHeads/${uid}`).get()).exists, false);
    await functions.onInviteAccepted.run({ data, params: { token, name: 'accept' } } as never);
    assert.equal((await db.doc(`users/${uid}`).get()).exists, false);
    assert.equal((await db.doc(`users/${deletingSide === 'inviter' ? joiner : inviter}`).get()).get('coupleId'), undefined);
    assert.equal((await db.doc(`pairings/${token}`).get()).exists, false);
  }
});

function appleIdentity(subject = 'apple-sub'): string {
  return `header.${Buffer.from(JSON.stringify({ iss: 'https://appleid.apple.com', aud: 'test.example.app', sub: subject,
    exp: Math.floor(Date.now() / 1_000) + 300 })).toString('base64url')}.signature`;
}

test('Apple 코드 교환 응답이 탈퇴보다 늦어도 토큰을 다시 저장하지 않고 폐기를 시도한다', async () => {
  const uid = 'late-apple';
  await getAuth().createUser({ uid });
  await getFirestore().doc(`appleTokens/${uid}`).set({ refreshToken: 'previous-refresh' });
  const exchanging = deferred();
  const deliver = deferred();
  let revoked = false;
  const request = mock.method(globalThis, 'fetch', (async (url: string | URL | Request, init?: RequestInit) => {
    if (String(url).endsWith('/auth/token')) {
      exchanging.resolve();
      await deliver.promise;
      return Response.json({ refresh_token: 'late-refresh', id_token: appleIdentity() });
    }
    if (String(url).endsWith('/auth/revoke')) { revoked = true; return new Response('', { status: 200 }); }
    return realFetch(url, init);
  }) as typeof fetch);
  const store = functions.appleStoreToken.run({ data: { code: 'local-code' }, auth: { uid, token: { firebase: { sign_in_provider: 'apple.com', identities: { 'apple.com': ['apple-sub'] } } } }, rawRequest: {} } as never);
  try {
    await exchanging.promise;
    assert.deepEqual(await remove(uid), { ok: true, appleRevocation: 'manual' });
    deliver.resolve();
    assert.deepEqual(await store, { stored: false });
    assert.equal(revoked, true);
    assert.equal((await getFirestore().doc(`appleTokens/${uid}`).get()).exists, false);
  } finally { deliver.resolve(); await store.catch(() => {}); request.mock.restore(); }
});

test('완료된 Apple 코드 교환은 대기 수를 줄이고 폐기 성공 결과를 반환한다', async () => {
  const uid = 'stored-apple';
  await getAuth().createUser({ uid });
  const request = mock.method(globalThis, 'fetch', (async (url: string | URL | Request, init?: RequestInit) => {
    if (String(url).endsWith('/auth/token')) return Response.json({ refresh_token: 'stored-refresh', id_token: appleIdentity() });
    if (String(url).endsWith('/auth/revoke')) return new Response('', { status: 200 });
    return realFetch(url, init);
  }) as typeof fetch);
  try {
    const stored = await functions.appleStoreToken.run({ data: { code: 'local-code' }, auth: { uid, token: { firebase: { sign_in_provider: 'apple.com', identities: { 'apple.com': ['apple-sub'] } } } }, rawRequest: {} } as never);
    assert.deepEqual(stored, { stored: true });
    assert.equal((await getFirestore().doc(`appleTokens/${uid}`).get()).get('pendingExchanges'), 0);
    assert.deepEqual(await remove(uid, 'apple-sub'), { ok: true, appleRevocation: 'revoked' });
  } finally { request.mock.restore(); }
});

test('Google 로그인 계정에 연결한 Apple 신원으로 토큰을 저장하고 준비 상태만 조회한다', async () => {
  const uid = 'google-linked-apple';
  await getAuth().createUser({ uid });
  const call = { data: { code: 'local-code' }, auth: { uid, token: { firebase: {
    sign_in_provider: 'google.com', identities: { 'apple.com': ['apple-sub'], 'google.com': ['google-sub'] },
  } } }, rawRequest: {} } as never;
  const request = mock.method(globalThis, 'fetch', (async (url: string | URL | Request, init?: RequestInit) => {
    if (String(url).endsWith('/auth/token')) return Response.json({ refresh_token: 'linked-refresh', id_token: appleIdentity() });
    return realFetch(url, init);
  }) as typeof fetch);
  try {
    assert.deepEqual(await functions.appleTokenState.run(call), { ready: false });
    assert.deepEqual(await functions.appleStoreToken.run(call), { stored: true });
    assert.deepEqual(await functions.appleTokenState.run(call), { ready: true });
    const token = getFirestore().doc(`appleTokens/${uid}`);
    await token.update({ pendingExchanges: 1 });
    assert.deepEqual(await functions.appleTokenState.run(call), { ready: false });
    await token.update({ pendingExchanges: 0, appleSubject: 'previous-apple' });
    assert.deepEqual(await functions.appleTokenState.run(call), { ready: false });
  } finally { request.mock.restore(); }
});

test('다른 Apple 계정의 code는 현재 계정에 저장하지 않고 발급 토큰을 폐기한다', async () => {
  const uid = 'mismatched-apple';
  await getAuth().createUser({ uid });
  let revoked = 0;
  const request = mock.method(globalThis, 'fetch', (async (url: string | URL | Request, init?: RequestInit) => {
    if (String(url).endsWith('/auth/token')) return Response.json({ refresh_token: 'wrong-account-token', id_token: appleIdentity('another-sub') });
    if (String(url).endsWith('/auth/revoke')) { revoked++; return new Response('', { status: 200 }); }
    return realFetch(url, init);
  }) as typeof fetch);
  try {
    await assert.rejects(functions.appleStoreToken.run({ data: { code: 'local-code' }, auth: { uid, token: { firebase: {
      sign_in_provider: 'apple.com', identities: { 'apple.com': ['apple-sub'] },
    } } }, rawRequest: {} } as never), /연결된 Apple 계정/);
    assert.equal((await getFirestore().doc(`appleTokens/${uid}`).get()).get('refreshToken'), undefined);
    assert.equal((await getFirestore().doc(`appleTokens/${uid}`).get()).get('pendingExchanges'), 0);
    assert.equal(revoked, 1);
  } finally { request.mock.restore(); }
});

test('편집 중 인증 계정이 바뀐 등록은 Apple 요청 전에 거부한다', async () => {
  const uid = 'apple-changed-owner';
  await getAuth().createUser({ uid });
  const request = mock.method(globalThis, 'fetch', (async () => { throw new Error('must not request Apple'); }) as typeof fetch);
  try {
    const call = { data: { code: 'local-code', expectedUid: 'previous-owner' }, auth: { uid, token: { firebase: {
      identities: { 'apple.com': ['apple-sub'] },
    } } }, rawRequest: {} } as never;
    await assert.rejects(functions.appleStoreToken.run(call), /계정이 바뀌었어요/);
    await assert.rejects(functions.appleTokenState.run(call), /계정이 바뀌었어요/);
    assert.equal(request.mock.callCount(), 0);
  } finally { request.mock.restore(); }
});

test('카카오 연결 해제 응답이 불확정이면 탈퇴 성공과 재가입을 허용하지 않는다', async () => {
  const request = mockKakao(1004);
  const old = await signIn((await kakao()).token);
  request.mock.restore();
  process.env.KAKAO_ADMIN_KEY = 'local-test';
  const uncertain = mock.method(globalThis, 'fetch', (async () => { throw new Error('lost-provider-response'); }) as typeof fetch);
  try {
    await assert.rejects(remove(old.localId), /lost-provider-response/);
    assert.equal((await deletionRef(old.localId).get()).get('state'), 'blocked');
    assert.equal((await deletionRef(old.localId).get()).get('reason'), 'provider-unlink-uncertain');
    await assert.rejects(remove(old.localId), /탈퇴 처리/);
  } finally { uncertain.mock.restore(); process.env.KAKAO_ADMIN_KEY = ''; }
  const loginRequest = mockKakao(1004);
  try { await assert.rejects(kakao(), /탈퇴 처리/); } finally { loginRequest.mock.restore(); }
});
