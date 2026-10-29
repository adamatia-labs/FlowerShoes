import { test } from 'node:test';
import assert from 'node:assert/strict';
import { answerDecision, isSecondWithin24h, requestPush, REQUEST_TTL_MS, withSubject } from '../device-change';

const NOW = Date.parse('2026-10-29T12:00:00Z');
const request = { uid: 'jisu', newDeviceId: 'jisu-new', status: 'pending', expireAtMs: NOW + REQUEST_TTL_MS };
const approver = { uid: 'minjun', active: true };

test('상대 활성 기기의 서명된 승인이면 활성화한다', () => {
  assert.equal(answerDecision(request, 'partner', { result: 'approve' }, approver, true, NOW), 'approve');
});

test('승인 서명이 틀리거나 · 요청자 본인이거나 · 폐기된 기기면 무시한다', () => {
  assert.equal(answerDecision(request, 'partner', { result: 'approve' }, approver, false, NOW), 'ignore');
  assert.equal(answerDecision(request, 'partner', { result: 'approve' }, { uid: 'jisu', active: true }, true, NOW), 'ignore');
  assert.equal(answerDecision(request, 'partner', { result: 'approve' }, { uid: 'minjun', active: false }, true, NOW), 'ignore');
  assert.equal(answerDecision(request, 'partner', { result: 'approve' }, undefined, true, NOW), 'ignore');
});

test('상대는 거절하고 · 요청한 사람은 취소하거나 막는다', () => {
  assert.equal(answerDecision(request, 'partner', { result: 'reject' }, undefined, false, NOW), 'reject');
  assert.equal(answerDecision(request, 'owner', { result: 'cancel' }, undefined, false, NOW), 'cancel');
  assert.equal(answerDecision(request, 'owner', { result: 'block' }, undefined, false, NOW), 'block');
  // 자리가 바뀐 답은 받지 않는다.
  assert.equal(answerDecision(request, 'owner', { result: 'approve' }, approver, true, NOW), 'ignore');
  assert.equal(answerDecision(request, 'partner', { result: 'block' }, undefined, false, NOW), 'ignore');
});

test('이미 끝났거나 만료된 요청에는 답하지 않는다', () => {
  assert.equal(answerDecision({ ...request, status: 'blocked' }, 'partner', { result: 'approve' }, approver, true, NOW), 'ignore');
  assert.equal(answerDecision({ ...request, status: 'approved' }, 'owner', { result: 'block' }, undefined, false, NOW), 'ignore');
  assert.equal(answerDecision({ ...request, expireAtMs: NOW }, 'partner', { result: 'approve' }, approver, true, NOW), 'ignore');
  assert.equal(answerDecision(undefined, 'partner', { result: 'approve' }, approver, true, NOW), 'ignore');
});

test('같은 사람의 24시간 안 두 번째 요청을 알아본다', () => {
  const at = (h: number) => NOW - h * 3600_000;
  assert.equal(isSecondWithin24h('jisu', NOW, [{ uid: 'jisu', createdAtMs: at(23) }]), true);
  assert.equal(isSecondWithin24h('jisu', NOW, [{ uid: 'jisu', createdAtMs: at(25) }]), false);
  assert.equal(isSecondWithin24h('jisu', NOW, [{ uid: 'minjun', createdAtMs: at(1) }]), false);
  assert.equal(isSecondWithin24h('jisu', NOW, []), false);
});

test('받침에 맞춰 이/가를 붙인다', () => {
  assert.equal(withSubject('지수'), '지수가');
  assert.equal(withSubject('민준'), '민준이');
  assert.equal(withSubject('Jun'), 'Jun가');
});

test('상대에게는 새 폰 확인을 · 같은 계정에는 로그인 알림을 보낸다', () => {
  assert.deepEqual(requestPush('지수'), {
    partner: '지수가 새 폰으로 바꿨대요. 맞나요?',
    owner: '새 폰에서 로그인했어요',
  });
});
