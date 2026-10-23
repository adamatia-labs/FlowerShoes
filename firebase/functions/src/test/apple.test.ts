import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { appleClientSecret, appleConfigured, exchangeCode, revoke } from '../apple';

const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const config = {
  teamId: 'TEAM123456',
  keyId: 'KEY1234567',
  clientId: 'com.sadturtleman.kkotsin',
  privateKey: privateKey.export({ format: 'pem', type: 'pkcs8' }).toString(),
};

test('client secret 은 Apple 이 요구하는 ES256 JWT 다', () => {
  const jwt = appleClientSecret(config, 1_000_000);
  const [h, p, s] = jwt.split('.');
  assert.deepEqual(JSON.parse(Buffer.from(h, 'base64url').toString()), { alg: 'ES256', kid: 'KEY1234567' });
  assert.deepEqual(JSON.parse(Buffer.from(p, 'base64url').toString()), {
    iss: 'TEAM123456', iat: 1_000_000, exp: 1_000_300, aud: 'https://appleid.apple.com', sub: 'com.sadturtleman.kkotsin',
  });
  const sig = Buffer.from(s, 'base64url');
  assert.equal(sig.length, 64, 'r||s 64바이트(DER 아님)');
  assert.ok(verify('sha256', Buffer.from(`${h}.${p}`), { key: publicKey, dsaEncoding: 'ieee-p1363' }, sig));
});

test('환경 변수에 줄바꿈이 이스케이프된 p8 도 읽는다', () => {
  const escaped = config.privateKey.split('\n').join('\\n');
  assert.ok(appleClientSecret({ ...config, privateKey: escaped }));
});

test('키가 하나라도 비면 설정 전', () => {
  assert.equal(appleConfigured(config), true);
  assert.equal(appleConfigured({ ...config, keyId: '' }), false);
});

test('code 교환 · 폐기 요청 모양', async () => {
  const calls: { url: string; body: URLSearchParams }[] = [];
  const fake = (async (url: string, init: RequestInit) => {
    calls.push({ url, body: new URLSearchParams(init.body as string) });
    return new Response(JSON.stringify({ refresh_token: 'rt-1' }), { status: 200 });
  }) as unknown as typeof fetch;

  assert.equal(await exchangeCode(config, 'code-1', fake), 'rt-1');
  assert.equal(calls[0].url, 'https://appleid.apple.com/auth/token');
  assert.equal(calls[0].body.get('grant_type'), 'authorization_code');
  assert.equal(calls[0].body.get('code'), 'code-1');

  assert.equal(await revoke(config, 'rt-1', fake), true);
  assert.equal(calls[1].url, 'https://appleid.apple.com/auth/revoke');
  assert.equal(calls[1].body.get('token'), 'rt-1');
  assert.equal(calls[1].body.get('token_type_hint'), 'refresh_token');
  assert.equal(calls[1].body.get('client_id'), 'com.sadturtleman.kkotsin');
});

test('Apple 이 거절하면 null', async () => {
  const fail = (async () => new Response('{}', { status: 400 })) as unknown as typeof fetch;
  assert.equal(await exchangeCode(config, 'bad', fail), null);
});
