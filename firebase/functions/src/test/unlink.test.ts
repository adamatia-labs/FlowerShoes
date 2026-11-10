import { test } from 'node:test';
import assert from 'node:assert/strict';
import { deliverUnlinkNotification, forgetUnlinkMember, unlink, UnlinkForbidden, UnlinkNotFound, UnlinkOperation, UnlinkStore } from '../unlink';

class FakeStore implements UnlinkStore {
  couple: string[] | null = ['a', 'b'];
  operation: UnlinkOperation | undefined;
  users = new Map([['a', 'old'], ['b', 'old']]);
  files = true;
  notifications: string[] = [];
  initiator = '';
  fail: 'couple' | 'files' | 'member' | null = null;

  async begin(_id: string, uid: string): Promise<UnlinkOperation> {
    if (!this.operation) {
      if (!this.couple) throw new UnlinkNotFound();
      if (!this.couple.includes(uid)) throw new UnlinkForbidden();
      this.initiator = uid;
      this.operation = { members: [...this.couple], completed: false };
    }
    if (!this.operation.members.includes(uid)) throw new UnlinkForbidden();
    return { ...this.operation };
  }
  async deleteCouple() {
    if (this.fail === 'couple') throw new Error('couple');
    this.couple = null;
  }
  async deleteFiles() {
    if (this.fail === 'files') throw new Error('files');
    this.files = false;
  }
  async clearMember(id: string, uid: string) {
    if (this.fail === 'member') throw new Error('member');
    if (this.users.get(uid) === id) this.users.delete(uid);
  }
  async complete() {
    if (this.operation!.completed) return;
    this.operation!.completed = true;
    const recipient = this.operation!.members.find((member) => member !== this.initiator);
    if (recipient) this.notifications.push(recipient);
  }
}

test('두 사람이 동시에 해제하거나 완료 후 재시도해도 성공한다', async () => {
  const store = new FakeStore();
  await Promise.all([unlink(store, 'old', 'a'), unlink(store, 'old', 'b')]);
  await unlink(store, 'old', 'a');
  assert.equal(store.operation?.completed, true);
  assert.equal(store.files, false);
  assert.equal(store.users.size, 0);
  assert.equal(store.notifications.length, 1);
  await assert.rejects(unlink(store, 'old', 'outsider'), UnlinkForbidden);
});

for (const phase of ['couple', 'files', 'member'] as const) {
  test(`${phase} 중 실패하면 작업을 완료로 기록하지 않고 다시 이어간다`, async () => {
    const store = new FakeStore();
    store.fail = phase;
    await assert.rejects(unlink(store, 'old', 'a'));
    assert.equal(store.operation?.completed, false);
    store.fail = null;
    await unlink(store, 'old', 'a');
    assert.equal(store.operation?.completed, true);
    assert.equal(store.files, false);
    assert.equal(store.users.size, 0);
  });
}

test('중단 중 다른 커플과 새로 연결된 사용자의 새 연결을 지우지 않는다', async () => {
  const store = new FakeStore();
  store.fail = 'files';
  await assert.rejects(unlink(store, 'old', 'a'));
  store.users.set('a', 'new');
  store.fail = null;
  await unlink(store, 'old', 'b');
  assert.equal(store.users.get('a'), 'new');
  assert.equal(store.users.has('b'), false);
});

test('모르는 사람은 작업을 만들거나 재시도할 수 없다', async () => {
  const store = new FakeStore();
  await assert.rejects(unlink(store, 'old', 'outsider'), UnlinkForbidden);
  assert.equal(store.operation, undefined);
  await unlink(store, 'old', 'a');
  await assert.rejects(unlink(store, 'old', 'outsider'), UnlinkForbidden);
});

test('탈퇴는 미완료 해제를 마친 뒤 본인 식별자와 대기 알림을 지운다', async () => {
  const store = new FakeStore();
  store.fail = 'files';
  await assert.rejects(unlink(store, 'old', 'a'));
  let removedNotifications = false;
  const history = {
    async operationsFor(uid: string) { return store.operation?.members.includes(uid) ? ['old'] : []; },
    async forgetMember(_coupleId: string, uid: string) {
      store.operation!.members = store.operation!.members.filter((member) => member !== uid);
      if (store.initiator === uid) store.initiator = '';
    },
    async forgetNotifications() { removedNotifications = true; },
  };
  await assert.rejects(forgetUnlinkMember(store, history, 'a'));
  assert.equal(store.operation?.members.includes('a'), true);
  assert.equal(removedNotifications, false);
  store.fail = null;
  await forgetUnlinkMember(store, history, 'a');
  assert.deepEqual(store.operation?.members, ['b']);
  assert.equal(store.initiator, '');
  assert.equal(removedNotifications, true);
  await unlink(store, 'old', 'b');
});

test('완료 시 알림 대기를 남겨 호출이 끝나기 전 종료돼도 전달할 수 있다', async () => {
  const store = new FakeStore();
  await unlink(store, 'old', 'a');
  await unlink(store, 'old', 'a');
  assert.deepEqual(store.notifications, ['b']);
});

test('알림 재전달은 완료된 항목을 무시하고 새 관계의 이전 알림을 숨긴다', async () => {
  let notice: { recipient: string; coupleId: string } | null = { recipient: 'b', coupleId: 'old' };
  let current: string | null = 'new';
  let sent = 0;
  const store = {
    async read() { return notice; },
    async currentCouple() { return current; },
    async send() { sent++; },
    async remove() { notice = null; },
  };
  await deliverUnlinkNotification(store);
  await deliverUnlinkNotification(store);
  assert.equal(sent, 0);
  current = null;
  notice = { recipient: 'b', coupleId: 'old' };
  await deliverUnlinkNotification(store);
  await deliverUnlinkNotification(store);
  assert.equal(sent, 1);
});

test('전송 뒤 삭제 전 실패하면 재시도에서 중복될 수 있다', async () => {
  let notice: { recipient: string; coupleId: string } | null = { recipient: 'b', coupleId: 'old' };
  let fail = true;
  let sent = 0;
  const store = {
    async read() { return notice; },
    async currentCouple() { return null; },
    async send() { sent++; },
    async remove() { if (fail) throw new Error('lost response'); notice = null; },
  };
  await assert.rejects(deliverUnlinkNotification(store));
  fail = false;
  await deliverUnlinkNotification(store);
  assert.equal(sent, 2);
});
