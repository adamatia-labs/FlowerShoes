import { after, before, mock, test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { getApp, deleteApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';

if (!process.env.FIRESTORE_EMULATOR_HOST || process.env.GCLOUD_PROJECT !== 'demo-kkotsin-apple') throw new Error('로컬 demo 에뮬레이터 전용');
let functions: typeof import('../index');
before(async () => {
  process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: 'demo-kkotsin-apple', storageBucket: 'demo-kkotsin-apple.appspot.com' });
  process.env.APPLE_TEAM_ID = 'TESTTEAM';
  process.env.APPLE_KEY_ID = 'TESTKEY';
  process.env.APPLE_CLIENT_ID = 'test.example.app';
  process.env.APPLE_PRIVATE_KEY = generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  functions = await import('../index');
});
after(async () => { mock.restoreAll(); await getFirestore().terminate(); await deleteApp(getApp()); });

test('Apple 토큰 폐기 성공, 실패와 토큰 부재를 구분하고 계정 데이터를 삭제한다', async () => {
  const remove = mock.method(getAuth(), 'deleteUser', async () => {});
  let ok = true;
  const request = mock.method(globalThis, 'fetch', (async (input: string | URL | Request) => {
    if (!String(input).endsWith('/auth/revoke')) throw new Error('unexpected test request');
    return new Response('', { status: ok ? 200 : 503 });
  }) as typeof fetch);
  try {
    for (const [uid, token, expected] of [
      ['apple-success', true, 'revoked'], ['apple-failure', true, 'manual'], ['apple-missing', false, 'manual'],
    ] as const) {
      ok = uid === 'apple-success';
      await getFirestore().doc(`users/${uid}`).set({ nickname: 'test' });
      if (token) await getFirestore().doc(`appleTokens/${uid}`).set({ refreshToken: 'test-token', appleSubject: 'apple-sub' });
      const result = await functions.deleteAccount.run({ auth: { uid, token: { firebase: { sign_in_provider: 'apple.com', identities: { 'apple.com': ['apple-sub'] } } } }, data: {}, rawRequest: {} } as never);
      assert.equal(result.appleRevocation, expected);
      assert.equal((await getFirestore().doc(`users/${uid}`).get()).exists, false);
      assert.equal((await getFirestore().doc(`appleTokens/${uid}`).get()).exists, false);
    }
  } finally { request.mock.restore(); remove.mock.restore(); }
});

test('토큰을 지운 뒤 Auth 정리가 실패해도 재시도와 응답 재전달은 같은 Apple 결과를 돌려준다', async () => {
  for (const revoked of [true, false]) {
    const uid = revoked ? 'apple-retry-revoked' : 'apple-retry-manual';
    const expected = revoked ? 'revoked' : 'manual';
    let fail = true;
    const remove = mock.method(getAuth(), 'deleteUser', async () => { if (fail) throw new Error('injected-auth-cleanup'); });
    const request = mock.method(globalThis, 'fetch', (async (input: string | URL | Request) => {
      if (!String(input).endsWith('/auth/revoke')) throw new Error('unexpected test request');
      return new Response('', { status: revoked ? 200 : 503 });
    }) as typeof fetch);
    const call = () => functions.deleteAccount.run({ auth: { uid, token: { firebase: { sign_in_provider: 'apple.com', identities: { 'apple.com': ['apple-sub'] } } } }, data: {}, rawRequest: {} } as never);
    try {
      await getFirestore().doc(`appleTokens/${uid}`).set({ refreshToken: 'test-token', appleSubject: 'apple-sub' });
      await assert.rejects(call(), /injected-auth-cleanup/);
      assert.equal((await getFirestore().doc(`appleTokens/${uid}`).get()).exists, false);
      assert.equal((await getFirestore().doc(`accountDeletions/${uid}`).get()).get('appleRevocation'), expected);
      fail = false;
      assert.deepEqual(await call(), { ok: true, appleRevocation: expected });
      assert.deepEqual(await call(), { ok: true, appleRevocation: expected });
      assert.equal(request.mock.callCount(), revoked ? 1 : 3);
      assert.equal(remove.mock.callCount(), 2);
    } finally { request.mock.restore(); remove.mock.restore(); }
  }
});

test('일시적인 Apple 오류는 토큰을 보관한 채 재시도하고 확인 뒤 삭제한다', async () => {
  const uid = 'apple-transient-retry';
  const tokenRef = getFirestore().doc(`appleTokens/${uid}`);
  let calls = 0;
  const remove = mock.method(getAuth(), 'deleteUser', async () => {});
  const request = mock.method(globalThis, 'fetch', (async (input: string | URL | Request) => {
    if (!String(input).endsWith('/auth/revoke')) throw new Error('unexpected test request');
    assert.equal((await tokenRef.get()).get('refreshToken'), 'test-token');
    return new Response('', { status: ++calls === 1 ? 503 : 200 });
  }) as typeof fetch);
  try {
    await tokenRef.set({ refreshToken: 'test-token', appleSubject: 'apple-sub' });
    await getFirestore().doc(`users/${uid}`).set({ nickname: 'local-test' });
    const result = await functions.deleteAccount.run({ auth: { uid, token: { firebase: { sign_in_provider: 'apple.com', identities: { 'apple.com': ['apple-sub'] } } } }, data: {}, rawRequest: {} } as never);
    assert.deepEqual(result, { ok: true, appleRevocation: 'revoked' });
    assert.equal(calls, 2);
    assert.equal((await tokenRef.get()).exists, false);
    assert.equal((await getFirestore().doc(`users/${uid}`).get()).exists, false);
  } finally { request.mock.restore(); remove.mock.restore(); }
});

test('현재 Apple 신원과 다른 토큰이나 신원 미확인 토큰은 폐기 성공이어도 수동 안내를 유지한다', async () => {
  const remove = mock.method(getAuth(), 'deleteUser', async () => {});
  const request = mock.method(globalThis, 'fetch', (async (input: string | URL | Request) => {
    if (!String(input).endsWith('/auth/revoke')) throw new Error('unexpected test request');
    return new Response('', { status: 200 });
  }) as typeof fetch);
  try {
    for (const subject of [undefined, 'previous-apple']) {
      const uid = subject ? 'apple-subject-changed' : 'apple-subject-legacy';
      const tokenRef = getFirestore().doc(`appleTokens/${uid}`);
      await tokenRef.set({ refreshToken: 'test-token', ...(subject ? { appleSubject: subject } : {}) });
      const result = await functions.deleteAccount.run({
        auth: { uid, token: { firebase: { sign_in_provider: 'apple.com', identities: { 'apple.com': ['current-apple'] } } } },
        data: {}, rawRequest: {},
      } as never);
      assert.deepEqual(result, { ok: true, appleRevocation: 'manual' });
      assert.equal((await tokenRef.get()).exists, false);
    }
    assert.equal(request.mock.callCount(), 2);
  } finally { request.mock.restore(); remove.mock.restore(); }
});
