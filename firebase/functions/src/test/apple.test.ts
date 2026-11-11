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

const idToken = (claims: Record<string, unknown> = {}) => `header.${Buffer.from(JSON.stringify({
  iss: 'https://appleid.apple.com', aud: config.clientId, sub: 'apple-sub', exp: Math.floor(Date.now() / 1_000) + 300, ...claims,
})).toString('base64url')}.signature`;

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
    return new Response(JSON.stringify({ refresh_token: 'rt-1', id_token: idToken() }), { status: 200 });
  }) as unknown as typeof fetch;

  assert.deepEqual(await exchangeCode(config, 'code-1', fake), { refreshToken: 'rt-1', subject: 'apple-sub' });
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

test('교환 응답의 신원이 잘못돼도 발급 토큰은 호출자가 폐기할 수 있게 돌려준다', async () => {
  for (const claims of [{ iss: 'other' }, { aud: 'other-client' }, { exp: 0 }, { sub: '' }]) {
    const request = (async () => Response.json({ refresh_token: 'issued', id_token: idToken(claims) })) as typeof fetch;
    assert.deepEqual(await exchangeCode(config, 'code', request), { refreshToken: 'issued', subject: null });
  }
});

test('일시적인 폐기 오류는 같은 토큰으로 재시도해 성공을 확인한다', async () => {
  let calls = 0;
  const request = (async () => new Response('', { status: ++calls === 1 ? 503 : 200 })) as typeof fetch;
  assert.equal(await revoke(config, 'refresh', request), true);
  assert.equal(calls, 2);
});

test('네트워크 단절 뒤에도 토큰 폐기를 다시 시도한다', async () => {
  let calls = 0;
  const request = (async () => {
    if (++calls === 1) throw new TypeError('network unavailable');
    return new Response('', { status: 200 });
  }) as typeof fetch;
  assert.equal(await revoke(config, 'refresh', request), true);
  assert.equal(calls, 2);
});

test('폐기 재시도 횟수를 제한하고 영구 오류는 반복하지 않는다', async () => {
  for (const status of [400, 401, 503]) {
    let calls = 0;
    const waits: number[] = [];
    const request = (async () => { calls++; return new Response('', { status }); }) as typeof fetch;
    assert.equal(await revoke(config, 'refresh', request, { wait: async ms => { waits.push(ms); } }), false);
    assert.equal(calls, status === 503 ? 3 : 1);
    assert.deepEqual(waits, status === 503 ? [250, 500] : []);
  }
});

test('Apple의 Retry-After를 지키고 긴 대기는 수동 안내로 넘긴다', async () => {
  for (const retryAfter of ['1', '60']) {
    let calls = 0;
    const waits: number[] = [];
    const request = (async () => ++calls === 1
      ? new Response('', { status: 429, headers: { 'Retry-After': retryAfter } })
      : new Response('', { status: 200 })) as typeof fetch;
    assert.equal(await revoke(config, 'refresh', request, { wait: async ms => { waits.push(ms); } }), retryAfter === '1');
    assert.deepEqual(waits, retryAfter === '1' ? [1_000] : []);
    assert.equal(calls, retryAfter === '1' ? 2 : 1);
  }
});

test('응답 없는 폐기는 각 요청을 중단하고 제한된 재시도 뒤 끝낸다', async () => {
  let aborted = 0;
  const request = (async (_url, init) => new Promise<Response>((_resolve, reject) => {
    init!.signal!.addEventListener('abort', () => { aborted++; reject(new DOMException('timeout', 'AbortError')); });
  })) as typeof fetch;
  assert.equal(await revoke(config, 'refresh', request, { timeoutMs: 5, wait: async () => {} }), false);
  assert.equal(aborted, 3);
});

test('일회용 code는 서버 오류에 재사용하지 않고 본문 지연도 중단한다', async () => {
  let calls = 0;
  const unavailable = (async () => { calls++; return new Response('', { status: 503 }); }) as typeof fetch;
  assert.equal(await exchangeCode(config, 'code', unavailable), null);
  assert.equal(calls, 1);

  const stalledBody = (async (_url, init) => new Response(new ReadableStream({
    start(controller) { init!.signal!.addEventListener('abort', () => controller.error(new DOMException('timeout', 'AbortError'))); },
  }))) as typeof fetch;
  await assert.rejects(exchangeCode(config, 'code', stalledBody, { timeoutMs: 5 }), { name: 'AbortError' });
});

test('본문 정리 오류가 이미 받은 HTTP 성공과 재시도 금지를 덮어쓰지 않는다', async () => {
  for (const status of [200, 400, 401, 429]) {
    let calls = 0;
    const request = (async () => {
      calls++;
      return new Response(new ReadableStream({ start(controller) { controller.error(new Error('connection reset')); } }),
        { status, headers: { 'Retry-After': '60' } });
    }) as typeof fetch;
    assert.equal(await revoke(config, 'refresh', request, { wait: async () => {} }), status === 200);
    assert.equal(calls, 1);
  }
});
