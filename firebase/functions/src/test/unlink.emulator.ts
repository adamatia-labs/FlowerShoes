// 실제 운영 프로젝트에 연결되지 않도록 명시적인 demo 에뮬레이터에서만 실행한다.
import { after, before, mock, test } from 'node:test';
import assert from 'node:assert/strict';
import { getApp, deleteApp } from 'firebase-admin/app';
import { getFirestore, QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';
import { resolve } from 'node:path';

if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.STORAGE_EMULATOR_HOST || process.env.GCLOUD_PROJECT !== 'demo-kkotsin-unlink') {
  throw new Error('Run npm run test:unlink:emulator with the local demo project only');
}

let functions: typeof import('../index');
before(async () => {
  process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: 'demo-kkotsin-unlink', storageBucket: 'demo-kkotsin-unlink.appspot.com' });
  functions = await import('../index');
});
after(async () => {
  mock.restoreAll();
  await getFirestore().terminate();
  await deleteApp(getApp());
});

async function seed(id: string) {
  const db = getFirestore();
  await db.doc(`couples/${id}`).set({ members: { [`${id}-a`]: 'device-a', [`${id}-b`]: 'device-b' } });
  await db.doc(`couples/${id}/inbox/device-a/ops/example`).set({ payload: 'example' });
  for (const user of [`${id}-a`, `${id}-b`]) await db.doc(`users/${user}`).set({ coupleId: id, deviceId: user });
  await getStorage().bucket().file(`couples/${id}/example`).save('local test');
}

async function call(id: string, uid: string) {
  return functions.unlinkCouple.run({ data: { coupleId: id }, auth: { uid, token: {} }, rawRequest: {} } as never);
}

test('실제 트랜잭션에서 두 사용자의 동시 해제와 재시도가 한 작업으로 끝난다', async () => {
  const id = 'both';
  const db = getFirestore();
  await seed(id);
  await Promise.all([call(id, `${id}-a`), call(id, `${id}-b`)]);
  await call(id, `${id}-a`);
  assert.equal((await db.doc(`couples/${id}`).get()).exists, false);
  assert.equal((await db.doc(`couples/${id}/inbox/device-a/ops/example`).get()).exists, false);
  assert.equal((await getStorage().bucket().getFiles({ prefix: `couples/${id}/` }))[0].length, 0);
  assert.equal((await db.doc(`users/${id}-a`).get()).get('coupleId'), undefined);
  assert.equal((await db.doc(`unlinkOperations/${id}`).get()).get('completed'), true);
  assert.equal((await db.doc(`unlinkNotifications/${id}`).get()).exists, true);
  await assert.rejects(call(id, 'outsider'));
});

test('Storage 실패 후 재시도는 완료되지 않은 작업을 이어가고 새 연결을 보존한다', async () => {
  const id = 'retry';
  const db = getFirestore();
  await seed(id);
  const bucket = getStorage().bucket();
  const prototype = Object.getPrototypeOf(bucket) as { deleteFiles: typeof bucket.deleteFiles };
  const failure = mock.method(prototype, 'deleteFiles', async () => { throw new Error('injected storage failure'); });
  try {
    await assert.rejects(call(id, `${id}-a`));
  } finally {
    failure.mock.restore();
  }
  assert.equal((await db.doc(`unlinkOperations/${id}`).get()).get('completed'), false);
  assert.equal((await db.doc(`unlinkNotifications/${id}`).get()).exists, false);
  await db.doc(`users/${id}-a`).update({ coupleId: 'new-couple', deviceId: 'new-device' });
  await call(id, `${id}-b`);
  assert.equal((await db.doc(`users/${id}-a`).get()).get('coupleId'), 'new-couple');
  assert.equal((await db.doc(`users/${id}-a`).get()).get('deviceId'), 'new-device');
  assert.equal((await db.doc(`unlinkOperations/${id}`).get()).get('completed'), true);
  assert.equal((await getStorage().bucket().getFiles({ prefix: `couples/${id}/` }))[0].length, 0);
});

test('새 관계가 있으면 실제 알림 트리거가 이전 커플 알림을 삭제하고 발송하지 않는다', async () => {
  const db = getFirestore();
  const ref = db.doc('unlinkNotifications/late');
  await db.doc('users/late-user').set({ coupleId: 'new-couple', fcmTokens: ['fake-local-token'] });
  await ref.set({ recipient: 'late-user', coupleId: 'late' });
  const data = await ref.get() as QueryDocumentSnapshot;
  const event = {
    data, params: { coupleId: 'late' }, project: 'demo-kkotsin-unlink', id: 'test', time: new Date().toISOString(),
    location: 'asia-northeast3', database: '(default)', namespace: '(default)', document: 'unlinkNotifications/late',
    specversion: '1.0' as const, type: 'google.cloud.firestore.document.v1.created', source: 'local-test',
  };
  await functions.onUnlinkNotificationCreated.run(event);
  await functions.onUnlinkNotificationCreated.run(event);
  assert.equal((await ref.get()).exists, false);
});

test('로그인하지 않은 클라이언트는 서버 전용 작업 문서를 읽거나 쓸 수 없다', async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST!;
  const url = `http://${host}/v1/projects/demo-kkotsin-unlink/databases/(default)/documents/unlinkOperations/both`;
  const read = await fetch(url);
  assert.equal(read.status, 403);
  const write = await fetch(url, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ fields: { completed: { booleanValue: true } } }) });
  assert.equal(write.status, 403);
});

test('작업의 실제 구성원도 서버 전용 작업과 알림 문서에 직접 접근할 수 없다', async () => {
  // 규칙 테스트 의존성은 이 저장소의 firebase/rules-test에서 설치한다.
  const paths = [resolve(process.cwd(), '../rules-test')];
  const rules = require(require.resolve('@firebase/rules-unit-testing', { paths }));
  const firestore = require(require.resolve('firebase/firestore', { paths }));
  const [host, port] = process.env.FIRESTORE_EMULATOR_HOST!.split(':');
  const env = await rules.initializeTestEnvironment({ projectId: 'demo-kkotsin-unlink', firestore: { host, port: Number(port) } });
  try {
    const client = env.authenticatedContext('both-a').firestore();
    for (const path of ['unlinkOperations/both', 'unlinkNotifications/both']) {
      await rules.assertFails(firestore.getDoc(firestore.doc(client, path)));
      await rules.assertFails(firestore.setDoc(firestore.doc(client, path), { completed: true }));
    }
  } finally {
    await env.cleanup();
  }
});

test('해제 작업 생성 직후부터 기존 커플 문서가 남아 있어도 구성원의 새 쓰기를 막는다', async () => {
  const id = 'frozen';
  await seed(id);
  await getFirestore().doc(`couples/${id}/devices/device-a`).set({ uid: `${id}-a`, active: true });
  const paths = [resolve(process.cwd(), '../rules-test')];
  const rules = require(require.resolve('@firebase/rules-unit-testing', { paths }));
  const firestore = require(require.resolve('firebase/firestore', { paths }));
  const [host, port] = process.env.FIRESTORE_EMULATOR_HOST!.split(':');
  const env = await rules.initializeTestEnvironment({ projectId: 'demo-kkotsin-unlink', firestore: { host, port: Number(port) } });
  try {
    const client = env.authenticatedContext(`${id}-a`).firestore();
    await rules.assertSucceeds(firestore.getDoc(firestore.doc(client, `couples/${id}`)));
    const payload = { kind: 'letter', from: 'device-a', ct: 'local-test' };
    await rules.assertSucceeds(firestore.setDoc(firestore.doc(client, `couples/${id}/inbox/device-b/ops/before`), payload));
    await getFirestore().doc(`unlinkOperations/${id}`).set({ members: [`${id}-a`, `${id}-b`], initiatedBy: `${id}-a`, completed: false });
    await rules.assertFails(firestore.setDoc(firestore.doc(client, `couples/${id}/inbox/device-b/ops/after`), payload));
    await rules.assertFails(firestore.getDoc(firestore.doc(client, `couples/${id}`)));
    await call(id, `${id}-a`);
    assert.equal((await getFirestore().doc(`couples/${id}/inbox/device-b/ops/before`).get()).exists, false);
  } finally { await env.cleanup(); }
});
