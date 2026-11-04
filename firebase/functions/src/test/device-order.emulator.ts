import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, getApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';

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
