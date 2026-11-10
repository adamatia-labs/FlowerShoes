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
      if (token) await getFirestore().doc(`appleTokens/${uid}`).set({ refreshToken: 'test-token' });
      const result = await functions.deleteAccount.run({ auth: { uid, token: { firebase: { sign_in_provider: 'apple.com' } } }, data: {}, rawRequest: {} } as never);
      assert.equal(result.appleRevocation, expected);
      assert.equal((await getFirestore().doc(`users/${uid}`).get()).exists, false);
      assert.equal((await getFirestore().doc(`appleTokens/${uid}`).get()).exists, false);
    }
  } finally { request.mock.restore(); remove.mock.restore(); }
});
