// 초대 링크(#15). Firestore · HTTP 를 모르는 순수 함수만 둔다 — index.ts 가 함수로 감싼다.

/** 초대 유효기간 7일. firestore.rules 의 expireAt 상한(7일 + 시계 오차 1시간)과 맞춘다. */
export const INVITE_TTL_MS = 7 * 24 * 3600 * 1000;

const TOKEN = /^[A-Za-z0-9_-]{22}$/;

/** 128비트 토큰의 base64url(패딩 없음) 22자. */
export function isInviteToken(s: string): boolean {
  return TOKEN.test(s);
}

/** Hosting rewrite 로 들어온 `/i/{token}` 경로에서 토큰. 형식이 아니면 null. */
export function tokenFromPath(path: string): string | null {
  const m = /^\/i\/([^/?#]+)\/?$/.exec(path);
  return m && isInviteToken(m[1]) ? m[1] : null;
}

export interface InviteDoc {
  uid: string;
  deviceId: string;
  pubKey: string;
  name: string;
  expireAtMs: number;
}

export interface AcceptDoc {
  uid: string;
  deviceId: string;
  pubKey: string;
}

/**
 * 수락을 받아들일지. 규칙이 작성자 · 1회를 막지만, 만료 · 이미 연결된 사람은 함수가 다시 본다.
 * reject 면 초대를 지운다(받는 쪽에는 만료로 보인다).
 */
export function acceptDecision(
  invite: InviteDoc | undefined,
  accept: AcceptDoc,
  nowMs: number,
  linked: { inviterLinked: boolean; joinerLinked: boolean },
): 'link' | 'reject' {
  if (!invite) return 'reject';
  if (invite.expireAtMs <= nowMs) return 'reject';
  if (invite.uid === accept.uid || invite.deviceId === accept.deviceId) return 'reject';
  if (linked.inviterLinked || linked.joinerLinked) return 'reject';
  if (!isPubKey(invite.pubKey) || !isPubKey(accept.pubKey)) return 'reject';
  return 'link';
}

/** pubKey = base64(signPub(32) ‖ boxPub(32)). */
export function isPubKey(s: string): boolean {
  return typeof s === 'string' && /^[A-Za-z0-9+/]{86}==$/.test(s);
}

export interface StoreUrls {
  play: string;
  appStore: string;
}

/** design.md 의 슬로건. 카카오 피드 설명 · OG 설명이 같다. */
const SLOGAN = '편지로 이어지는 우리 둘만의 달력';

const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');

/**
 * `/i/{token}` 미리보기 · 랜딩 페이지. 이름 외 개인 정보(uid · 기기 · 공개키)는 싣지 않는다.
 * 앱이 있으면 App Links · Universal Links 가 먼저 가로채고, 여기는 앱이 없거나 인앱 브라우저일 때 보인다.
 */
export function invitePageHtml(p: { name: string | undefined; expired: boolean; url: string; stores: StoreUrls }): string {
  const title = p.expired ? '꽃신 초대' : p.name ? `${p.name}님이 꽃신에 초대했어요` : '연인이 꽃신에 초대했어요';
  const heading = p.expired ? '이 초대 링크는 만료됐어요' : title;
  const note = p.expired ? '' : '<p class="note">설치 후 이 링크를 다시 눌러 주세요</p>';
  const t = escapeHtml(title);
  // 이름 없는 공용 이미지(hosting/public/og). 미리보기 크롤러가 따라가도록 초대 호스트의 절대 주소.
  const ogImage = `${new URL(p.url).origin}/og/invite-1200x630.png`;
  return `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${t}</title>
<meta property="og:type" content="website">
<meta property="og:site_name" content="꽃신">
<meta property="og:title" content="${t}">
<meta property="og:description" content="${SLOGAN}">
<meta property="og:url" content="${escapeHtml(p.url)}">
<meta property="og:image" content="${escapeHtml(ogImage)}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<meta name="robots" content="noindex">
<style>
  /* 테마 v2 곰신 토큰(core/designsystem Colors.kt): canvas · ink · sub · accent, 라이트 / 다크 */
  :root { --canvas: #F7F1E8; --ink: #3B302B; --sub: #74665C; --accent: #B9475E; --on-accent: #F7F1E8; }
  @media (prefers-color-scheme: dark) { :root { --canvas: #1F1A17; --ink: #EFE6DA; --sub: #B8AB9E; --accent: #E07A8F; --on-accent: #1F1A17; } }
  body { margin: 0; font-family: -apple-system, 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif; background: var(--canvas); color: var(--ink); }
  main { max-width: 420px; margin: 0 auto; padding: 64px 20px; display: flex; flex-direction: column; gap: 16px; }
  h1 { font-size: 22px; margin: 0 0 8px; }
  .note { color: var(--sub); font-size: 15px; margin: 0; }
  a.store { display: block; text-align: center; padding: 16px; border-radius: 12px; background: var(--accent); color: var(--on-accent); text-decoration: none; font-weight: 600; }
</style>
</head>
<body>
<main>
  <h1>${escapeHtml(heading)}</h1>
  <a class="store" href="${escapeHtml(p.stores.play)}">Google Play</a>
  <a class="store" href="${escapeHtml(p.stores.appStore)}">App Store</a>
  ${note}
</main>
</body>
</html>
`;
}
