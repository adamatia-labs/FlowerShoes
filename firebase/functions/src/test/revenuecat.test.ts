import { test } from 'node:test';
import assert from 'node:assert/strict';
import { entitlementsFrom, refreshEntitlements, usersOf } from '../revenuecat';

const now = new Date('2026-10-24T00:00:00Z');

test('평생은 책 · 용량 추가 말고 활성 이용권, 만료된 것은 뺀다', () => {
  const body = { subscriber: { entitlements: {
    lifetime: { expires_date: null },
    memory_book: { expires_date: null },
    backup_plus: { expires_date: '2026-01-01T00:00:00Z' },
  } } };
  assert.deepEqual(entitlementsFrom(body, now), { lifetime: true, book: true, backupPlus: false });
  assert.deepEqual(entitlementsFrom({ subscriber: { entitlements: { memory_book: {} } } }, now), { lifetime: false, book: true, backupPlus: false });
  assert.deepEqual(entitlementsFrom({}, now), { lifetime: false, book: false, backupPlus: false });
});

test('웹훅 사용자: 익명 id 는 빼고 옮겨진 계정도 다시 읽는다', () => {
  assert.deepEqual(usersOf({ event: { app_user_id: 'kakao:1', original_app_user_id: '$RCAnonymousID:x', transferred_to: ['google:2'] } }), ['kakao:1', 'google:2']);
  assert.deepEqual(usersOf({}), []);
  assert.deepEqual(usersOf({ event: { type: 'TEST', app_user_id: 'dashboard-generated-uuid' } }), []);
});

test('조회 오류와 잘못된 응답은 이용권을 덮어쓰지 않고 재시도를 요구한다', async () => {
  const saved: string[] = [];
  for (const response of [new Response('', { status: 401 }), new Response('', { status: 503 }), Response.json({})]) {
    await assert.rejects(refreshEntitlements({ event: { app_user_id: 'user' } }, 'test-key', async uid => { saved.push(uid); }, async () => response));
  }
  await assert.rejects(refreshEntitlements({}, '', async uid => { saved.push(uid); }));
  assert.deepEqual(saved, []);
});

test('계정 이전은 두 계정의 현재 권한을 각각 다시 저장한다', async () => {
  const saved: unknown[] = [];
  const requested: string[] = [];
  await refreshEntitlements({ event: { transferred_from: ['old'], transferred_to: ['new'] } }, 'test-key', async (uid, ent) => {
    saved.push({ uid, ...ent });
  }, async url => {
    requested.push(String(url));
    return Response.json({ subscriber: { entitlements: String(url).endsWith('/new') ? { lifetime: {} } : {} } });
  });
  assert.equal(requested.length, 2);
  assert.deepEqual(saved, [
    { uid: 'old', lifetime: false, book: false, backupPlus: false },
    { uid: 'new', lifetime: true, book: false, backupPlus: false },
  ]);
});
