/**
 * 백업 보관 기간(약관 제10조의2, 수익 모델 확정): 무료는 마지막 사용 후 1년, 꽃신 평생은 10년.
 * 지우기 30일 · 7일 전에 알린다. 그 사이 앱을 쓰면 lastSeenAt 이 바뀌어 기간이 다시 시작된다.
 */
export const DAY_MS = 24 * 3600 * 1000;
export const FREE_KEEP_DAYS = 365;
export const LIFETIME_KEEP_DAYS = 3650;
export const WARN_DAYS = [30, 7] as const;

export type RetentionAction = { type: 'none' } | { type: 'warn'; daysLeft: number } | { type: 'delete' };

/**
 * [warned] 는 이번 기간(같은 lastSeenAt)에 이미 보낸 예고 날 수들. 같은 예고를 두 번 보내지 않는다.
 * 예고를 하나도 못 보낸 채 기한이 지났으면(함수가 멈춰 있었던 경우) 지우기 전에 7일 예고부터 보낸다.
 */
export function retentionAction(lastSeenMs: number, lifetime: boolean, nowMs: number, warned: number[]): RetentionAction {
  const keep = (lifetime ? LIFETIME_KEEP_DAYS : FREE_KEEP_DAYS) * DAY_MS;
  const left = Math.ceil((lastSeenMs + keep - nowMs) / DAY_MS);
  if (left <= 0) return warned.length > 0 ? { type: 'delete' } : { type: 'warn', daysLeft: 7 };
  const due = WARN_DAYS.find((d) => left <= d && !warned.includes(d));
  return due !== undefined ? { type: 'warn', daysLeft: due } : { type: 'none' };
}

export function warnMessage(daysLeft: number): { title: string; body: string } {
  return {
    title: '꽃신 백업 보관 안내',
    body: `${daysLeft}일 뒤 백업이 지워져요. 앱을 열면 계속 보관되고, 추억 책 PDF · 내 기록 내보내기로 남길 수 있어요`,
  };
}
