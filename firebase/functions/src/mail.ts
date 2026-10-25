import { createHash, randomUUID } from 'node:crypto';

export const MAX_MAIL_ATTEMPTS = 5;
// 함수의 120초 제한보다 길게 잡아 동시 이벤트가 같은 메일을 보내지 않게 한다.
export const MAIL_LEASE_MS = 5 * 60 * 1000;
export type Mail = { to: string; message: { subject: string; text: string } };
export type Delivery = {
  state: 'PROCESSING' | 'SUCCESS' | 'RETRY' | 'ERROR';
  attempts: number;
  claimId: string;
  leaseUntilMs: number;
  updatedAtMs: number;
  sentAtMs?: number;
  errorCode?: string;
};
export type MailClaim = { data: unknown; delivery: Delivery } | 'busy' | 'skip';
export interface MailStore {
  claim(now: number, claimId: string): Promise<MailClaim>;
  finish(claimId: string, delivery: Delivery): Promise<boolean>;
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown> : {};
}

export function isEmail(value: unknown): value is string {
  return typeof value === 'string' && value.length <= 254
    && /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\.[A-Za-z]{2,63}$/.test(value);
}

/** 평문 제목/본문과 수신자 한 명만 허용. HTML·첨부·임의 헤더는 전달하지 않는다. */
export function parseMail(value: unknown): Mail | undefined {
  const data = record(value);
  const message = record(data.message);
  if (!isEmail(data.to) || typeof message.subject !== 'string' || !message.subject.trim()
    || message.subject.length > 200 || /[\r\n\0]/.test(message.subject)
    || typeof message.text !== 'string' || !message.text.trim() || message.text.length > 20_000) return;
  return { to: data.to, message: { subject: message.subject, text: message.text } };
}

export function nextMailClaim(value: unknown, now: number, claimId: string): Delivery | 'busy' | 'skip' {
  const old = record(value);
  if (old.state === 'SUCCESS' || old.state === 'ERROR') return 'skip';
  if (old.state === 'PROCESSING' && typeof old.leaseUntilMs === 'number' && old.leaseUntilMs > now) return 'busy';
  const attempts = typeof old.attempts === 'number' && Number.isSafeInteger(old.attempts) && old.attempts >= 0
    ? old.attempts : 0;
  if (attempts >= MAX_MAIL_ATTEMPTS) {
    return { state: 'ERROR', attempts, claimId, leaseUntilMs: 0, updatedAtMs: now, errorCode: 'ATTEMPTS_EXHAUSTED' };
  }
  return { state: 'PROCESSING', attempts: attempts + 1, claimId, leaseUntilMs: now + MAIL_LEASE_MS, updatedAtMs: now };
}

/** SMTP 오류 원문에는 수신자/서버 응답/인증 정보가 섞일 수 있어 고정 코드만 보관한다. */
export function mailFailure(error: unknown): { retry: boolean; code: string } {
  const e = record(error);
  if (typeof e.responseCode === 'number' && e.responseCode >= 400 && e.responseCode < 500) {
    return { retry: true, code: 'SMTP_TEMPORARY' };
  }
  if (e.code === 'EAUTH') return { retry: false, code: 'SMTP_AUTH' };
  if (typeof e.responseCode === 'number' && e.responseCode >= 500 && e.responseCode < 600) {
    return { retry: false, code: 'SMTP_REJECTED' };
  }
  if (e.code === 'MAIL_CONFIG') return { retry: false, code: 'MAIL_CONFIG' };
  return { retry: true, code: 'SMTP_UNAVAILABLE' };
}

export function backupWarningId(uid: string, lastSeenMs: number, daysLeft: number): string {
  return 'backup-' + createHash('sha256').update(JSON.stringify([uid, lastSeenMs, daysLeft])).digest('hex');
}

export function mailMessageId(projectId: string, documentId: string, from: string): string {
  const hash = createHash('sha256').update(JSON.stringify([projectId, documentId])).digest('hex');
  return `<${hash}@${from.split('@')[1]}>`;
}

export type MailResult = 'sent' | 'skipped' | 'failed';

/** SMTP 수락과 Firestore 저장은 원자적이지 않아, 전송 직후 프로세스가 멈추면 중복 수신될 수 있다. */
export async function deliverMail(
  store: MailStore,
  send: (mail: Mail) => Promise<void>,
  now: () => number = Date.now,
): Promise<MailResult> {
  const claimId = randomUUID();
  const claim = await store.claim(now(), claimId);
  if (claim === 'skip') return 'skipped';
  if (claim === 'busy') throw new Error('MAIL_LEASE_BUSY'); // Eventarc가 나중에 다시 호출
  if (claim.delivery.state === 'ERROR') return 'failed';
  const mail = parseMail(claim.data);
  let failure: { retry: boolean; code: string } | undefined;
  if (!mail) failure = { retry: false, code: 'INVALID_MESSAGE' };
  else {
    try { await send(mail); }
    catch (error) { failure = mailFailure(error); }
  }
  const retry = failure?.retry === true && claim.delivery.attempts < MAX_MAIL_ATTEMPTS;
  const finishedAt = now();
  const delivery: Delivery = {
    ...claim.delivery, state: failure ? (retry ? 'RETRY' : 'ERROR') : 'SUCCESS',
    leaseUntilMs: 0, updatedAtMs: finishedAt,
    ...(failure ? { errorCode: failure.code } : { sentAtMs: finishedAt }),
  };
  if (!await store.finish(claimId, delivery)) throw new Error('MAIL_CLAIM_LOST');
  if (retry) throw new Error('MAIL_RETRY'); // 원본 SMTP 오류는 던지거나 기록하지 않는다.
  return failure ? 'failed' : 'sent';
}
