import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DAY_MS, retentionAction } from '../retention';

const now = Date.UTC(2027, 9, 24);
const seen = (daysAgo: number) => now - daysAgo * DAY_MS;

test('무료: 1년 30일 전 · 7일 전 예고, 지나면 지운다', () => {
  assert.deepEqual(retentionAction(seen(300), false, now, []), { type: 'none' });
  assert.deepEqual(retentionAction(seen(335), false, now, []), { type: 'warn', daysLeft: 30 });
  assert.deepEqual(retentionAction(seen(340), false, now, [30]), { type: 'none' });
  assert.deepEqual(retentionAction(seen(358), false, now, [30]), { type: 'warn', daysLeft: 7 });
  assert.deepEqual(retentionAction(seen(366), false, now, [30, 7]), { type: 'delete' });
});

test('꽃신 평생은 10년 보관', () => {
  assert.deepEqual(retentionAction(seen(400), true, now, []), { type: 'none' });
  assert.deepEqual(retentionAction(seen(3620), true, now, []), { type: 'warn', daysLeft: 30 });
});

test('예고 없이 기한이 지났으면 먼저 알린다', () => {
  assert.deepEqual(retentionAction(seen(400), false, now, []), { type: 'warn', daysLeft: 7 });
});
