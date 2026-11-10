// Sign in with Apple 토큰 폐기(App Store 심사 기준 5.1.1(v): 앱 안 탈퇴 시 Apple 토큰 폐기).
//
// 1) iOS 가 로그인 때 받은 일회용 authorization code → appleStoreToken → /auth/token → refresh token 을 appleTokens/{uid}(클라이언트 접근 불가)에 보관
// 2) 탈퇴(deleteAccount) 때 /auth/revoke
import { createPrivateKey, sign } from 'node:crypto';

export interface AppleConfig {
  teamId: string;
  keyId: string;
  /** 앱 번들 id(Sign in with Apple 의 client_id). */
  clientId: string;
  /** .p8 내용(PEM). 환경 변수에서는 줄바꿈이 \n 으로 들어와도 된다. */
  privateKey: string;
}

export const appleConfigured = (c: AppleConfig) => !!(c.teamId && c.keyId && c.clientId && c.privateKey);

const b64url = (b: Buffer | string) => Buffer.from(b).toString('base64url');

/** client_secret: ES256 JWT. Apple 문서 "Creating a client secret". */
export function appleClientSecret(c: AppleConfig, nowSec: number = Math.floor(Date.now() / 1000)): string {
  const header = b64url(JSON.stringify({ alg: 'ES256', kid: c.keyId }));
  const payload = b64url(JSON.stringify({ iss: c.teamId, iat: nowSec, exp: nowSec + 300, aud: 'https://appleid.apple.com', sub: c.clientId }));
  const key = createPrivateKey(c.privateKey.replace(/\\n/g, '\n'));
  const sig = sign('sha256', Buffer.from(`${header}.${payload}`), { key, dsaEncoding: 'ieee-p1363' });
  return `${header}.${payload}.${b64url(sig)}`;
}

type Fetch = typeof fetch;

interface RequestOptions {
  timeoutMs?: number;
  wait?: (ms: number) => Promise<void>;
}

/** 응답 본문까지 제한한다. 헤더만 도착한 교환 요청도 무한히 기다리지 않는다. */
async function request<T>(url: string, init: RequestInit, f: Fetch, timeoutMs: number, read: (r: Response) => Promise<T>): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try { return await read(await f(url, { ...init, signal: controller.signal })); }
  finally { clearTimeout(timer); }
}

function form(fields: Record<string, string>): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(fields).toString(),
  };
}

/** authorization code → refresh token. 거절되면 null. */
export async function exchangeCode(c: AppleConfig, code: string, f: Fetch = fetch, options: RequestOptions = {}): Promise<string | null> {
  // 일회용 code는 응답을 잃었을 때 이미 소비됐을 수 있으므로 자동 재시도하지 않는다.
  return request('https://appleid.apple.com/auth/token', form({
    client_id: c.clientId, client_secret: appleClientSecret(c), code, grant_type: 'authorization_code',
  }), f, options.timeoutMs ?? 5_000, async res => {
    if (!res.ok) { await res.body?.cancel().catch(() => undefined); return null; }
    const json = (await res.json()) as { refresh_token?: string };
    return json.refresh_token ?? null;
  });
}

/** 폐기는 같은 토큰으로 최대 3회. 실패하면 호출자가 데이터 삭제와 수동 해제 안내를 이어간다. */
export async function revoke(c: AppleConfig, refreshToken: string, f: Fetch = fetch, options: RequestOptions = {}): Promise<boolean> {
  const init = form({
    client_id: c.clientId, client_secret: appleClientSecret(c), token: refreshToken, token_type_hint: 'refresh_token',
  });
  const wait = options.wait ?? (ms => new Promise(resolve => setTimeout(resolve, ms)));
  for (let attempt = 0; attempt < 3; attempt++) {
    let pause = 250 * (attempt + 1);
    try {
      const res = await request('https://appleid.apple.com/auth/revoke', init, f, options.timeoutMs ?? 5_000, async res => {
        // HTTP 결과는 이미 확정됐다. 본문 정리 오류로 성공/재시도 금지를 바꾸지 않는다.
        await res.body?.cancel().catch(() => undefined);
        return res;
      });
      if (res.ok) return true;
      if (res.status !== 408 && res.status !== 429 && res.status < 500) return false;
      const retryAfter = res.headers.get('Retry-After');
      if (retryAfter) {
        const seconds = Number(retryAfter);
        const requested = Number.isFinite(seconds) ? seconds * 1_000 : Date.parse(retryAfter) - Date.now();
        // 긴 대기 요청을 무시하고 재호출하지 않는다. 탈퇴를 진행하고 수동 해제를 안내한다.
        if (requested > 2_000) return false;
        if (Number.isFinite(requested)) pause = Math.max(pause, requested);
      }
    } catch {
      // 네트워크 오류·시간 초과만 같은 폐기 요청으로 복구한다. 토큰/응답은 로그에 싣지 않는다.
    }
    if (attempt < 2) await wait(pause);
  }
  return false;
}
