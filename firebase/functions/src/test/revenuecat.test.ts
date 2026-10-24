import { test } from 'node:test';
import assert from 'node:assert/strict';
import { entitlementsFrom, usersOf } from '../revenuecat';

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
});
