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

function form(fields: Record<string, string>): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(fields).toString(),
  };
}

/** authorization code → refresh token. 거절되면 null. */
export async function exchangeCode(c: AppleConfig, code: string, f: Fetch = fetch): Promise<string | null> {
  const res = await f('https://appleid.apple.com/auth/token', form({
    client_id: c.clientId, client_secret: appleClientSecret(c), code, grant_type: 'authorization_code',
  }));
  if (!res.ok) return null;
  const json = (await res.json()) as { refresh_token?: string };
  return json.refresh_token ?? null;
}

export async function revoke(c: AppleConfig, refreshToken: string, f: Fetch = fetch): Promise<boolean> {
  const res = await f('https://appleid.apple.com/auth/revoke', form({
    client_id: c.clientId, client_secret: appleClientSecret(c), token: refreshToken, token_type_hint: 'refresh_token',
  }));
  return res.ok;
}
