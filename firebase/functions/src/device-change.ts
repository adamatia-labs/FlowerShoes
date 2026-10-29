// 기기 교체(#20). Firestore 를 모르는 순수 함수만 둔다 — index.ts 가 함수로 감싼다.

/** 요청 유효기간 7일. firestore.rules 의 expireAt 상한(7일 + 시계 오차 1시간)과 맞춘다. */
export const REQUEST_TTL_MS = 7 * 24 * 3600 * 1000;

const DAY_MS = 24 * 3600 * 1000;

export interface RequestState {
  uid: string;
  newDeviceId: string;
  status: string;
  expireAtMs: number;
}

/** answer/{who}: partner = 상대 활성 기기(approve · reject), owner = 요청한 계정(cancel · block). */
export type AnswerSide = 'partner' | 'owner';

export type Outcome = 'approve' | 'reject' | 'cancel' | 'block' | 'ignore';

/**
 * 답을 받아들일지. 규칙이 작성자 · 1회를 막지만, 끝난 요청 · 만료 · 승인자 기기 · 서명은 함수가 다시 본다.
 * [approver] 는 승인 서명을 한 기기(couples/{c}/devices/{approverDeviceId}), [signatureOk] 는 그 기기 키로 확인한 결과.
 */
export function answerDecision(
  request: RequestState | undefined,
  side: AnswerSide,
  answer: { result?: unknown },
  approver: { uid: string; active: boolean } | undefined,
  signatureOk: boolean,
  nowMs: number,
): Outcome {
  if (!request || request.status !== 'pending' || request.expireAtMs <= nowMs) return 'ignore';
  if (side === 'partner') {
    if (answer.result === 'reject') return 'reject';
    if (answer.result !== 'approve') return 'ignore';
    if (!approver || !approver.active || approver.uid === request.uid || !signatureOk) return 'ignore';
    return 'approve';
  }
  if (answer.result === 'cancel') return 'cancel';
  if (answer.result === 'block') return 'block';
  return 'ignore';
}

/** 같은 사람이 24시간 안에 이미 요청했다(상대 화면에 경고 + 한 번 더 확인). */
export function isSecondWithin24h(uid: string, createdAtMs: number, others: { uid: string; createdAtMs: number }[]): boolean {
  return others.some((o) => o.uid === uid && o.createdAtMs < createdAtMs && createdAtMs - o.createdAtMs < DAY_MS);
}

/** 이름 + 주격 조사(앱 settings withSubject 와 같다: 한글 받침이 있으면 "이", 아니면 "가"). */
export function withSubject(name: string): string {
  const code = name.charCodeAt(name.length - 1);
  const batchim = code >= 0xac00 && code <= 0xd7a3 && (code - 0xac00) % 28 !== 0;
  return name + (batchim ? '이' : '가');
}

/** 새 요청 알림 문구: 상대 폰(Figma 연인 폰 · 승인 요청 카드)과 같은 계정의 옛 폰. */
export function requestPush(name: string): { partner: string; owner: string } {
  return { partner: `${withSubject(name)} 새 폰으로 바꿨대요. 맞나요?`, owner: '새 폰에서 로그인했어요' };
}

/** 옛 폰이 막았을 때 상대에게. */
export function blockedPush(name: string): string {
  return `${withSubject(name)} 새 폰 요청을 막았어요`;
}
