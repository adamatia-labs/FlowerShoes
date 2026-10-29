/**
 * RevenueCat 웹훅 → entitlements/{uid}. 웹훅 본문은 믿지 않고, 알림이 오면 REST API 로 그 사용자의 지금 이용권을 다시 읽는다.
 * RevenueCat 사용자 id = 꽃신 계정 id(앱이 logIn 한다).
 */
export const BOOK_ENTITLEMENT = 'memory_book';

export interface Entitlements {
  lifetime: boolean;
  book: boolean;
}

type SubscriberEntitlement = { expires_date?: string | null };

/** /v1/subscribers/{id} 응답 → 활성 이용권. 평생은 추억 책이 아닌 활성 entitlement 하나라도. */
export function entitlementsFrom(body: unknown, now: Date): Entitlements {
  const ents = ((body as { subscriber?: { entitlements?: Record<string, SubscriberEntitlement> } })?.subscriber?.entitlements) ?? {};
  const active = Object.entries(ents)
    .filter(([, e]) => !e.expires_date || new Date(e.expires_date).getTime() > now.getTime())
    .map(([id]) => id);
  return {
    lifetime: active.some((id) => id !== BOOK_ENTITLEMENT),
    book: active.includes(BOOK_ENTITLEMENT),
  };
}

/** 웹훅 본문에서 다시 읽을 사용자들(이전 · 새 id 모두 — TRANSFER 대비). 익명($RCAnonymousID)은 뺀다. */
export function usersOf(body: unknown): string[] {
  const e = (body as { event?: { type?: string; app_user_id?: string; original_app_user_id?: string; transferred_from?: string[]; transferred_to?: string[] } })?.event;
  if (!e || e.type === 'TEST') return [];
  const ids = [e.app_user_id, e.original_app_user_id, ...(e.transferred_from ?? []), ...(e.transferred_to ?? [])];
  return [...new Set(ids.filter((id): id is string => !!id && !id.startsWith('$RCAnonymousID')))];
}

/** 조회 실패를 성공으로 확인하지 않는다. 호출자는 5xx 로 응답해 RevenueCat 재전송을 받는다. */
export async function refreshEntitlements(
  body: unknown,
  key: string,
  save: (uid: string, entitlements: Entitlements) => Promise<void>,
  request: typeof fetch = fetch,
): Promise<void> {
  if (!key) throw new Error('RevenueCat server key is missing');
  for (const uid of usersOf(body)) {
    const response = await request(`https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(uid)}`, {
      headers: { Authorization: `Bearer ${key}` },
    });
    if (!response.ok) throw new Error(`RevenueCat subscriber lookup failed (${response.status})`);
    const result = await response.json() as { subscriber?: { entitlements?: unknown } };
    const entitlements = result?.subscriber?.entitlements;
    if (!entitlements || typeof entitlements !== 'object' || Array.isArray(entitlements)) {
      throw new Error('RevenueCat subscriber response is invalid');
    }
    await save(uid, entitlementsFrom(result, new Date()));
  }
}
