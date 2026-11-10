import { randomUUID } from 'node:crypto';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';

/** Never expire these server-only tombstones: an already issued custom token can recreate an old Auth UID. */
export const deletionRef = (uid: string) => getFirestore().doc(`accountDeletions/${uid}`);

export async function requireLiveAccount(uid: string): Promise<void> {
  if ((await deletionRef(uid).get()).exists) {
    throw new HttpsError('failed-precondition', '탈퇴한 계정이에요. 다시 로그인해 주세요.', { reason: 'ACCOUNT_DELETED', reauthenticate: true });
  }
}

/** Legacy UIDs stay valid until deletion. Each subsequent Kakao registration gets a new UID. */
export async function kakaoAccountUid(providerId: string, loginStartedAtMs: number): Promise<string> {
  const db = getFirestore();
  const ref = db.doc(`kakaoAccounts/${providerId}`);
  const nextUid = `kakao:${providerId}:${randomUUID()}`;
  return db.runTransaction(async (tx) => {
    const mapping = await tx.get(ref);
    if (mapping.exists && loginStartedAtMs <= (mapping.get('notBeforeMs') as number ?? 0)) {
      throw new HttpsError('unauthenticated', '탈퇴 전에 시작한 로그인이에요. 카카오 로그인을 다시 시작해 주세요.', { reason: 'ACCOUNT_SESSION_EXPIRED', reauthenticate: true });
    }
    const current = mapping.exists ? mapping.get('uid') as string : `kakao:${providerId}`;
    const deletion = await tx.get(deletionRef(current));
    if (deletion.exists && deletion.get('state') !== 'deleted') {
      throw new HttpsError('failed-precondition', '이전 계정의 탈퇴 처리를 확인하고 있어요. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.', { reason: 'ACCOUNT_DELETION_PENDING' });
    }
    const notBeforeMs = deletion.get('completedAt')?.toMillis() as number | undefined;
    if (deletion.exists && (notBeforeMs === undefined || loginStartedAtMs <= notBeforeMs)) {
      throw new HttpsError('unauthenticated', '탈퇴 전에 시작한 로그인이에요. 카카오 로그인을 다시 시작해 주세요.', { reason: 'ACCOUNT_SESSION_EXPIRED', reauthenticate: true });
    }
    const uid = deletion.exists ? nextUid : current;
    if (!mapping.exists || uid !== current) tx.set(ref, { uid, ...(notBeforeMs === undefined ? {} : { notBeforeMs }) });
    return uid;
  });
}

export function kakaoProviderId(uid: string): string | undefined {
  return /^kakao:(\d+)(?::[a-f0-9-]+)?$/.exec(uid)?.[1];
}

export type AppleRevocationResult = 'not_required' | 'revoked' | 'manual';
export type DeletionResult = { ok: true; appleRevocation: AppleRevocationResult };
export type DeletionClaim = { attempt: string; appleRevocation?: AppleRevocationResult; result?: never } | { result: DeletionResult; attempt?: never };

/** Only one executor may reach provider/Auth deletion. An abandoned claim requires operator verification, not a lease timeout. */
export async function claimAccountDeletion(uid: string): Promise<DeletionClaim> {
  const db = getFirestore();
  const ref = deletionRef(uid);
  const attempt = randomUUID();
  return db.runTransaction(async (tx) => {
    const deletion = await tx.get(ref);
    if (deletion.get('state') === 'deleted') return { result: deletion.get('result') as DeletionResult };
    if (deletion.exists && deletion.get('state') !== 'retryable') {
      throw new HttpsError('failed-precondition', '탈퇴 처리 결과를 확인하고 있어요. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.', { reason: 'ACCOUNT_DELETION_PENDING' });
    }
    tx.set(ref, { state: 'deleting', attempt, phase: 'cleanup', startedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { attempt, appleRevocation: deletion.get('appleRevocation') as AppleRevocationResult | undefined };
  });
}

/** Persist before token deletion: a later database/Auth failure must not turn a confirmed result into 'missing token'. */
export async function rememberAppleRevocation(uid: string, attempt: string, appleRevocation: AppleRevocationResult): Promise<void> {
  await getFirestore().runTransaction(async (tx) => {
    const ref = deletionRef(uid);
    const deletion = await tx.get(ref);
    if (deletion.get('attempt') !== attempt || deletion.get('state') !== 'deleting') throw new Error('ACCOUNT_DELETION_OWNERSHIP_LOST');
    tx.update(ref, { appleRevocation });
  });
}

export async function deletionPhase(uid: string, attempt: string, phase: string): Promise<void> {
  await getFirestore().runTransaction(async (tx) => {
    const ref = deletionRef(uid);
    const deletion = await tx.get(ref);
    if (deletion.get('attempt') !== attempt || deletion.get('state') !== 'deleting') throw new Error('ACCOUNT_DELETION_OWNERSHIP_LOST');
    tx.update(ref, { phase });
  });
}

/** A settled local error is retryable. Ambiguous provider I/O stays blocked so it cannot revoke a later registration. */
export async function failAccountDeletion(uid: string, attempt: string, uncertain: boolean): Promise<void> {
  await getFirestore().runTransaction(async (tx) => {
    const ref = deletionRef(uid);
    const deletion = await tx.get(ref);
    if (deletion.get('attempt') !== attempt) return;
    tx.update(ref, { state: uncertain ? 'blocked' : 'retryable', reason: uncertain ? 'provider-unlink-uncertain' : 'cleanup-failed', attempt: FieldValue.delete() });
  });
}

export async function completeAccountDeletion(uid: string, attempt: string, result: DeletionResult): Promise<void> {
  await getFirestore().runTransaction(async (tx) => {
    const ref = deletionRef(uid);
    const deletion = await tx.get(ref);
    if (deletion.get('attempt') !== attempt || deletion.get('state') !== 'deleting') throw new Error('ACCOUNT_DELETION_OWNERSHIP_LOST');
    tx.set(ref, { state: 'deleted', result, completedAt: FieldValue.serverTimestamp() });
  });
}
