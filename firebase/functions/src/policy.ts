// 알림 정책(설계 6-4). Firestore · FCM 을 모르는 순수 함수 — 테스트로 묶는다.
//
// iOS 는 앱(NSE)에서 알림을 숨길 수 없으므로 보낼지 · 묶을지는 서버가 정한다.
// 서버는 내용은 못 보고 종류(kind) · 시각 · 크기만 본다.

export type Kind =
  | 'missyou' | 'status' | 'letter' | 'letter-drawing' | 'scheduled' | 'event'
  | 'receipt' | 'delete' | 'snapshot' | 'profile';

export interface NotifyPrefs {
  /** 서울 기준 분(0..1439). 기본 23:00–07:00. */
  quietStart: number;
  quietEnd: number;
  tz: string;
  missYouBundle: number;
  kinds?: Partial<Record<Kind, boolean>>;
}

export const DEFAULT_PREFS: NotifyPrefs = { quietStart: 23 * 60, quietEnd: 7 * 60, tz: 'Asia/Seoul', missYouBundle: 10 };

export interface NotifyState {
  /** 보고싶어 횟수를 세는 날(서울 기준 yyyy-mm-dd). 날이 바뀌면 0부터. */
  missYouDay: string;
  missYouCount: number;
}

export interface InboxDoc {
  kind: Kind | string;
  resend?: boolean;
  /** 인라인으로 실을 암호문(base64). 원본 2KB 이하일 때만 있다. */
  inline?: string;
}

export type Decision =
  | { type: 'visible'; title: string; body: string; inline?: string }
  | { type: 'data'; reason: string }
  | { type: 'none'; reason: string };

/** 화면 알림 없이 data 메시지로만 깨우는 종류. */
const SILENT: ReadonlySet<string> = new Set(['receipt', 'delete', 'snapshot', 'profile']);

/** 서울 기준 그날의 분과 날짜. Asia/Seoul 은 서머타임이 없어 UTC+9 고정이다. */
export function seoulClock(now: Date): { minute: number; day: string } {
  const shifted = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  return {
    minute: shifted.getUTCHours() * 60 + shifted.getUTCMinutes(),
    day: shifted.toISOString().slice(0, 10),
  };
}

/** 조용한 시간인지. 자정을 넘는 구간(23:00–07:00)도 맞게 본다. */
export function isQuiet(prefs: NotifyPrefs, minute: number): boolean {
  if (prefs.quietStart === prefs.quietEnd) return false;
  return prefs.quietStart < prefs.quietEnd
    ? minute >= prefs.quietStart && minute < prefs.quietEnd
    : minute >= prefs.quietStart || minute < prefs.quietEnd;
}

/**
 * 우편함 문서 하나에 대해 무엇을 보낼지 정한다. 보고싶어 횟수는 새 상태로 돌려준다.
 *
 * - 재전송(resend) · 조용한 종류는 data 만.
 * - 조용한 시간에는 화면 알림 없음(Android 는 위젯 갱신용 data, iOS 는 아침 요약).
 * - 보고싶어: 그날 첫 번째는 바로, 이후는 [missYouBundle] 번째마다 "보고싶어 N번".
 */
export function decide(
  doc: InboxDoc,
  prefs: NotifyPrefs,
  state: NotifyState,
  now: Date,
  senderName: string,
): { decision: Decision; state: NotifyState } {
  const { minute, day } = seoulClock(now);
  let next = state.missYouDay === day ? state : { missYouDay: day, missYouCount: 0 };

  if (doc.resend) return { decision: { type: 'data', reason: 'resend' }, state: next };
  if (SILENT.has(doc.kind)) return { decision: { type: 'data', reason: 'silent-kind' }, state: next };
  if (prefs.kinds && prefs.kinds[doc.kind as Kind] === false) {
    return { decision: { type: 'data', reason: 'kind-off' }, state: next };
  }

  if (doc.kind === 'missyou') next = { ...next, missYouCount: next.missYouCount + 1 };
  if (isQuiet(prefs, minute)) return { decision: { type: 'data', reason: 'quiet' }, state: next };

  if (doc.kind === 'missyou') {
    const n = next.missYouCount;
    const bundle = Math.max(1, prefs.missYouBundle);
    if (n === 1) return { decision: { type: 'visible', title: senderName, body: `${senderName}이(가) 보고싶대요`, inline: doc.inline }, state: next };
    if (n % bundle === 0) return { decision: { type: 'visible', title: senderName, body: `오늘 보고싶어 ${n}번`, inline: doc.inline }, state: next };
    return { decision: { type: 'data', reason: 'bundled' }, state: next };
  }

  const body: Record<string, string> = {
    status: `${senderName}의 상태가 바뀌었어요`,
    letter: `${senderName}의 편지가 도착했어요`,
    'letter-drawing': `${senderName}이(가) 그림을 보냈어요`,
    // 예약 편지는 도착해도 열 수 없으니 공개 시각에 받는 폰이 로컬 알림으로 알린다.
    scheduled: '',
    event: `${senderName}이(가) 일정을 바꿨어요`,
  };
  const text = body[doc.kind];
  if (text === undefined) return { decision: { type: 'data', reason: 'unknown-kind' }, state: next };
  if (text === '') return { decision: { type: 'data', reason: 'scheduled' }, state: next };
  return { decision: { type: 'visible', title: senderName, body: text, inline: doc.inline }, state: next };
}

/**
 * 조용한 시간 끝 색인. notifyPrefs 를 저장할 때 서버가 계산해 둔다 — 15분마다 도는 아침 요약 함수가
 * "지난 15분 안에 조용한 시간이 끝난 사람"을 색인으로 찾는다.
 */
export function quietEndUtcMinute(prefs: NotifyPrefs): number {
  return ((prefs.quietEnd - 9 * 60) % 1440 + 1440) % 1440;
}

/** 아침 요약 대상인지: 지금(UTC 분)이 조용한 시간 끝 이후 [windowMin] 분 안. */
export function inDigestWindow(endUtcMinute: number, nowUtcMinute: number, windowMin = 15): boolean {
  const diff = ((nowUtcMinute - endUtcMinute) % 1440 + 1440) % 1440;
  return diff < windowMin;
}

/** 인라인 페이로드 한도: 암호화 전 원본 2KB → base64 후 약 2.7KB. FCM · APNs 4KB 안에 알림 필드와 함께 들어간다. */
export const INLINE_MAX_BASE64 = 2800;
