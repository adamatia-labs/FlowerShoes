import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, getApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { generateKeyPairSync, sign } from 'node:crypto';
import { approvalMessage } from '../sig';

if (!process.env.FIRESTORE_EMULATOR_HOST || process.env.GCLOUD_PROJECT !== 'demo-kkotsin-device') throw new Error('로컬 기기 테스트 환경이 아니다');
let functions: typeof import('../index');
before(async () => {
  process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: 'demo-kkotsin-device' });
  functions = await import('../index');
});
after(async () => { await getFirestore().terminate(); await deleteApp(getApp()); });

test('늦게 처리된 이전 요청이 더 최근 기기 요청을 취소하지 않는다', async () => {
  const db = getFirestore();
  const couple = 'out-of-order';
  const base = db.collection(`couples/${couple}/deviceChanges`);
  const older = base.doc('older');
  const newer = base.doc('newer');
  await db.doc(`couples/${couple}`).set({ members: { owner: 'old-device', partner: 'partner-device' } });
  await db.doc(`couples/${couple}/devices/older-device`).set({ uid: 'owner' });
  await db.doc(`couples/${couple}/devices/newer-device`).set({ uid: 'owner' });
  await older.set({ uid: 'owner', newDeviceId: 'older-device', status: 'pending', createdAt: Timestamp.fromMillis(1000) });
  await newer.set({ uid: 'owner', newDeviceId: 'newer-device', status: 'pending', createdAt: Timestamp.fromMillis(2000) });
  await functions.onDeviceChangeCreated.run({ data: await older.get(), params: { c: couple, r: older.id } } as never);
  await functions.onDeviceChangeCreated.run({ data: await newer.get(), params: { c: couple, r: newer.id } } as never);
  assert.equal((await older.get()).get('status'), 'cancelled');
  assert.equal((await newer.get()).get('status'), 'pending');
  assert.equal((await newer.get()).get('second'), true);
  assert.equal((await db.doc(`couples/${couple}/devices/newer-device`).get()).exists, true);
});

test('동일 시각 요청은 이벤트 재전송에도 고정된 순서로 처리한다', async () => {
  const db = getFirestore();
  const couple = 'same-time';
  const base = db.collection(`couples/${couple}/deviceChanges`);
  const earlier = base.doc('a');
  const latest = base.doc('b');
  const timestamp = new Timestamp(10, 200);
  await db.doc(`couples/${couple}`).set({ members: { owner: 'old-device', partner: 'partner-device' } });
  await db.doc(`couples/${couple}/devices/a-device`).set({ uid: 'owner' });
  await db.doc(`couples/${couple}/devices/b-device`).set({ uid: 'owner' });
  await earlier.set({ uid: 'owner', newDeviceId: 'a-device', status: 'pending', createdAt: timestamp });
  await latest.set({ uid: 'owner', newDeviceId: 'b-device', status: 'pending', createdAt: timestamp });
  const event = { data: await latest.get(), params: { c: couple, r: latest.id } } as never;
  await functions.onDeviceChangeCreated.run(event);
  await functions.onDeviceChangeCreated.run(event);
  await functions.onDeviceChangeCreated.run({ data: await earlier.get(), params: { c: couple, r: earlier.id } } as never);
  assert.equal((await earlier.get()).get('status'), 'cancelled');
  assert.equal((await latest.get()).get('status'), 'pending');
  assert.equal((await latest.get()).get('second'), true);
  assert.equal((await db.doc(`couples/${couple}/devices/b-device`).get()).exists, true);
});

test('두 생성 이벤트가 동시에 실행되어도 최근 요청만 유지한다', async () => {
  const db = getFirestore();
  const couple = 'concurrent';
  const base = db.collection(`couples/${couple}/deviceChanges`);
  const older = base.doc('older');
  const newer = base.doc('newer');
  await db.doc(`couples/${couple}`).set({ members: { owner: 'old-device', partner: 'partner-device' } });
  await db.doc(`couples/${couple}/devices/older-device`).set({ uid: 'owner' });
  await db.doc(`couples/${couple}/devices/newer-device`).set({ uid: 'owner' });
  await older.set({ uid: 'owner', newDeviceId: 'older-device', status: 'pending', createdAt: Timestamp.fromMillis(1000) });
  await newer.set({ uid: 'owner', newDeviceId: 'newer-device', status: 'pending', createdAt: Timestamp.fromMillis(2000) });
  const events = await Promise.all([older.get(), newer.get()]);
  await Promise.all(events.map((data) => functions.onDeviceChangeCreated.run({ data, params: { c: couple, r: data.id } } as never)));
  assert.equal((await older.get()).get('status'), 'cancelled');
  assert.equal((await newer.get()).get('status'), 'pending');
  assert.equal((await newer.get()).get('second'), true);
  assert.equal((await db.doc(`couples/${couple}/devices/newer-device`).get()).exists, true);
});

test('새 요청이 이미 생긴 뒤 이전 요청의 승인이 처리되어도 이전 기기가 활성화되지 않는다', async () => {
  const db = getFirestore();
  const couple = 'late-approval';
  const base = db.collection(`couples/${couple}/deviceChanges`);
  const older = base.doc('older');
  const newer = base.doc('newer');
  const keys = generateKeyPairSync('ed25519');
  const pub = keys.publicKey.export({ format: 'der', type: 'spki' }).subarray(-32).toString('base64');
  await db.doc(`couples/${couple}`).set({ members: { owner: 'old-device', partner: 'partner-device' } });
  await db.doc(`couples/${couple}/devices/old-device`).set({ uid: 'owner', active: true });
  await db.doc(`couples/${couple}/devices/partner-device`).set({ uid: 'partner', active: true, signPub: pub });
  await db.doc(`couples/${couple}/devices/older-device`).set({ uid: 'owner', active: false, state: 'pending' });
  await db.doc(`couples/${couple}/devices/newer-device`).set({ uid: 'owner', active: false, state: 'pending' });
  const expires = Timestamp.fromMillis(Date.now() + 60000);
  await older.set({ uid: 'owner', newDeviceId: 'older-device', status: 'pending', createdAt: Timestamp.fromMillis(1000), expireAt: expires });
  await newer.set({ uid: 'owner', newDeviceId: 'newer-device', status: 'pending', createdAt: Timestamp.fromMillis(2000), expireAt: expires });
  const sig = sign(null, approvalMessage(couple, older.id, 'older-device', {}), keys.privateKey).toString('base64');
  const answer = older.collection('answer').doc('partner');
  await answer.set({ result: 'approve', approverDeviceId: 'partner-device', cutoff: {}, sig });
  await functions.onDeviceChangeAnswered.run({ data: await answer.get(), params: { c: couple, r: older.id, who: 'partner' } } as never);
  assert.equal((await db.doc(`couples/${couple}`).get()).get('members.owner'), 'old-device');
  assert.equal((await older.get()).get('status'), 'cancelled');
  assert.equal((await newer.get()).get('status'), 'pending');
  assert.equal((await db.doc(`couples/${couple}/devices/newer-device`).get()).exists, true);
  const newestSig = sign(null, approvalMessage(couple, newer.id, 'newer-device', {}), keys.privateKey).toString('base64');
  const newestAnswer = newer.collection('answer').doc('partner');
  await newestAnswer.set({ result: 'approve', approverDeviceId: 'partner-device', cutoff: {}, sig: newestSig });
  await functions.onDeviceChangeAnswered.run({ data: await newestAnswer.get(), params: { c: couple, r: newer.id, who: 'partner' } } as never);
  assert.equal((await db.doc(`couples/${couple}`).get()).get('members.owner'), 'newer-device');
  assert.equal((await newer.get()).get('status'), 'approved');
});

test('이전 요청 취소와 새 요청이 같은 대기 기기를 가리키면 기기를 보존한다', async () => {
  const db = getFirestore();
  const couple = 'shared-pending-device';
  const base = db.collection(`couples/${couple}/deviceChanges`);
  const older = base.doc('older');
  const newer = base.doc('newer');
  await db.doc(`couples/${couple}`).set({ members: { owner: 'old-device', partner: 'partner-device' } });
  const device = db.doc(`couples/${couple}/devices/shared-device`);
  await device.set({ uid: 'owner', active: false, state: 'pending' });
  const expires = Timestamp.fromMillis(Date.now() + 60000);
  await older.set({ uid: 'owner', newDeviceId: 'shared-device', status: 'pending', createdAt: Timestamp.fromMillis(1000), expireAt: expires });
  await newer.set({ uid: 'owner', newDeviceId: 'shared-device', status: 'pending', createdAt: Timestamp.fromMillis(2000), expireAt: expires });
  const answer = older.collection('answer').doc('owner');
  await answer.set({ result: 'cancel' });
  await functions.onDeviceChangeAnswered.run({ data: await answer.get(), params: { c: couple, r: older.id, who: 'owner' } } as never);
  assert.equal((await older.get()).get('status'), 'cancelled');
  assert.equal((await newer.get()).get('status'), 'pending');
  assert.equal((await device.get()).exists, true);
});
