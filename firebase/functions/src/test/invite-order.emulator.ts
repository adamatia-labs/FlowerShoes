import { after, before, mock, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, getApp } from 'firebase-admin/app';
import { DocumentReference, DocumentSnapshot, getFirestore, Timestamp } from 'firebase-admin/firestore';

if (!process.env.FIRESTORE_EMULATOR_HOST || process.env.GCLOUD_PROJECT !== 'demo-kkotsin-invite') throw new Error('Local demo Firestore required');
let functions: typeof import('../index');
before(async () => {
  process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: 'demo-kkotsin-invite' });
  functions = await import('../index');
});
after(async () => { mock.restoreAll(); await getFirestore().terminate(); await deleteApp(getApp()); });

const pubKey = Buffer.alloc(64, 1).toString('base64');
const future = () => Timestamp.fromMillis(Date.now() + 60_000);
const payload = (uid: string, expireAt = future()) => ({ uid, deviceId: `${uid}-device`, pubKey, name: '', expireAt });
const created = (data: DocumentSnapshot) => functions.onInviteCreated.run({ data, params: { token: data.id } } as never);
async function accept(token: string, uid: string) {
  const ref = getFirestore().doc(`pairings/${token}/steps/accept`);
  await ref.set({ uid, deviceId: `${uid}-device`, pubKey });
  await functions.onInviteAccepted.run({ data: await ref.get(), params: { token, name: 'accept' } } as never);
}

test('동일 만료와 서버 생성 시각에서도 두 생성 실행이 서로를 지우지 않는다', async () => {
  const db = getFirestore();
  const a = db.doc('pairings/same-a');
  const b = db.doc('pairings/same-b');
  const batch = db.batch();
  const fields = payload('same');
  batch.set(a, fields); batch.set(b, fields); await batch.commit();
  const snapshots = await Promise.all([a.get(), b.get()]);
  assert.equal(snapshots[0].createTime!.isEqual(snapshots[1].createTime!), true);
  // Hold deletions until both handlers have observed the same pair of documents.
  const pending: DocumentReference[] = [];
  const recursiveDelete = db.recursiveDelete.bind(db);
  const hold = mock.method(db, 'recursiveDelete', async (ref: DocumentReference) => { pending.push(ref); });
  try { await Promise.all(snapshots.map(created)); } finally { hold.mock.restore(); }
  assert.equal((await b.get()).get('orderRecorded'), true);
  assert.equal((await db.doc('inviteHeads/same').get()).get('token'), b.id);
  await Promise.all(pending.map((ref) => recursiveDelete(ref)));
  assert.equal((await a.get()).exists, false);
  assert.equal((await b.get()).exists, true);
  await created(snapshots[0]); await created(snapshots[1]);
  assert.equal((await b.get()).exists, true);
});

test('기기 시계 때문에 새 링크 만료가 빨라도 서버 생성 순서를 따른다', async () => {
  const db = getFirestore();
  const old = db.doc('pairings/skew-old');
  const next = db.doc('pairings/skew-new');
  await old.set(payload('skew', Timestamp.fromMillis(Date.now() + 120_000)));
  const oldEvent = await old.get();
  await next.set(payload('skew'));
  await created(await next.get()); await created(oldEvent);
  assert.equal((await old.get()).exists, false);
  assert.equal((await next.get()).exists, true);
});

test('새 링크 정리 트리거 전에도 이전 링크 수락은 관계를 만들지 않는다', async () => {
  const db = getFirestore();
  const old = db.doc('pairings/early-old');
  const next = db.doc('pairings/early-new');
  await old.set(payload('early')); await next.set(payload('early'));
  await accept(old.id, 'early-joiner');
  assert.equal((await db.doc('users/early').get()).get('coupleId'), undefined);
  assert.equal((await db.doc('users/early-joiner').get()).get('coupleId'), undefined);
  assert.equal((await next.get()).exists, true);
  await accept(next.id, 'early-joiner');
  const coupleId = (await db.doc('users/early').get()).get('coupleId');
  assert.equal(typeof coupleId, 'string');
  assert.equal((await db.doc('users/early-joiner').get()).get('coupleId'), coupleId);
});

test('정리 실패 후 최신 링크가 사라져도 이전 링크가 되살아나지 않는다', async () => {
  const db = getFirestore();
  const old = db.doc('pairings/retry-old');
  const next = db.doc('pairings/retry-new');
  await old.set(payload('retry')); await next.set(payload('retry'));
  const event = await next.get();
  const fail = mock.method(db, 'recursiveDelete', async () => { throw new Error('temporary cleanup failure'); });
  try { await assert.rejects(created(event)); } finally { fail.mock.restore(); }
  assert.equal((await next.get()).get('orderRecorded'), true);
  assert.equal((await db.doc('inviteHeads/retry').get()).get('token'), next.id);
  await next.delete();
  await accept(old.id, 'retry-joiner');
  assert.equal((await db.doc('users/retry').get()).get('coupleId'), undefined);
  await created(event);
  assert.equal((await old.get()).exists, false);
});

test('취소된 최신 링크의 지연 생성 이벤트도 이전 링크를 무효화한다', async () => {
  const db = getFirestore();
  const old = db.doc('pairings/cancelled-old');
  const next = db.doc('pairings/cancelled-new');
  await old.set(payload('cancelled')); const oldEvent = await old.get();
  await next.set(payload('cancelled')); const event = await next.get();
  await next.delete();
  await created(event); await created(oldEvent);
  assert.equal((await old.get()).exists, false);
  assert.equal((await next.get()).exists, false);
  assert.equal((await db.doc('inviteHeads/cancelled').get()).get('token'), next.id);
});

test('탈퇴 표식 이후 지연 생성 이벤트는 최신 초대 메타도 되살리지 않는다', async () => {
  const db = getFirestore();
  const ref = db.doc('pairings/deleted-inviter');
  await ref.set(payload('deleted-inviter')); const event = await ref.get();
  await db.doc('accountDeletions/deleted-inviter').set({ state: 'deleted' });
  await created(event);
  assert.equal((await ref.get()).exists, false);
  assert.equal((await db.doc('inviteHeads/deleted-inviter').get()).exists, false);
});
