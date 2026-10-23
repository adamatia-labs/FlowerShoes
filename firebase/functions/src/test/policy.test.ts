import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decide, DEFAULT_PREFS, inDigestWindow, isQuiet, quietEndUtcMinute, seoulClock, NotifyState } from '../policy';

// 서울 시각 → UTC Date
const seoul = (iso: string) => new Date(new Date(`${iso}Z`).getTime() - 9 * 3600 * 1000);
const fresh: NotifyState = { missYouDay: '', missYouCount: 0 };

test('서울 기준 분과 날짜', () => {
  assert.deepEqual(seoulClock(seoul('2026-10-22T23:30:00')), { minute: 23 * 60 + 30, day: '2026-10-22' });
});

test('조용한 시간은 자정을 넘어도 맞게 본다', () => {
  assert.equal(isQuiet(DEFAULT_PREFS, 23 * 60), true);
  assert.equal(isQuiet(DEFAULT_PREFS, 3 * 60), true);
  assert.equal(isQuiet(DEFAULT_PREFS, 7 * 60), false);
  assert.equal(isQuiet({ ...DEFAULT_PREFS, quietStart: 60, quietEnd: 120 }, 90), true);
});

test('보고싶어는 첫 번째 바로, 이후 10번째마다 묶는다', () => {
  let state = fresh;
  const kinds: string[] = [];
  for (let i = 0; i < 20; i++) {
    const r = decide({ kind: 'missyou' }, DEFAULT_PREFS, state, seoul('2026-10-22T12:00:00'), '민준');
    state = r.state;
    kinds.push(r.decision.type);
  }
  const visibleAt = kinds.map((k, i) => (k === 'visible' ? i + 1 : 0)).filter(Boolean);
  assert.deepEqual(visibleAt, [1, 10, 20]);
});

test('날이 바뀌면 보고싶어 횟수를 다시 센다', () => {
  const day1 = decide({ kind: 'missyou' }, DEFAULT_PREFS, fresh, seoul('2026-10-22T12:00:00'), '민준').state;
  const day2 = decide({ kind: 'missyou' }, DEFAULT_PREFS, day1, seoul('2026-10-23T12:00:00'), '민준');
  assert.equal(day2.state.missYouCount, 1);
  assert.equal(day2.decision.type, 'visible');
});

test('조용한 시간에는 화면 알림 없이 data 만, 횟수는 센다', () => {
  const r = decide({ kind: 'missyou' }, DEFAULT_PREFS, fresh, seoul('2026-10-22T23:30:00'), '민준');
  assert.equal(r.decision.type, 'data');
  assert.equal(r.state.missYouCount, 1);
});

test('재전송 · 조용한 종류 · 예약 편지는 화면 알림이 없다', () => {
  const noon = seoul('2026-10-22T12:00:00');
  assert.equal(decide({ kind: 'letter', resend: true }, DEFAULT_PREFS, fresh, noon, '민준').decision.type, 'data');
  assert.equal(decide({ kind: 'receipt' }, DEFAULT_PREFS, fresh, noon, '민준').decision.type, 'data');
  assert.equal(decide({ kind: 'scheduled' }, DEFAULT_PREFS, fresh, noon, '민준').decision.type, 'data');
  const letter = decide({ kind: 'letter-drawing' }, DEFAULT_PREFS, fresh, noon, '민준').decision;
  assert.equal(letter.type, 'visible');
  assert.match(letter.type === 'visible' ? letter.body : '', /그림을 보냈어요/);
});

test('받는 사람이 끈 종류는 data 만', () => {
  const prefs = { ...DEFAULT_PREFS, kinds: { status: false } };
  assert.equal(decide({ kind: 'status' }, prefs, fresh, seoul('2026-10-22T12:00:00'), '민준').decision.type, 'data');
});

test('아침 요약 색인과 창', () => {
  // 서울 07:00 = UTC 22:00
  assert.equal(quietEndUtcMinute(DEFAULT_PREFS), 22 * 60);
  assert.equal(inDigestWindow(22 * 60, 22 * 60 + 10), true);
  assert.equal(inDigestWindow(22 * 60, 22 * 60 + 15), false);
  assert.equal(inDigestWindow(23 * 60 + 55, 5), true); // 자정 넘김
});
