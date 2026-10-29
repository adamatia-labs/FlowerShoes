import { test } from 'node:test';
import assert from 'node:assert/strict';
import { kakaoNickname } from '../kakao';

test('카카오 사용자 정보에서 닉네임을 꺼낸다(10자, 앞뒤 공백 제거)', () => {
  assert.equal(kakaoNickname({ id: 1, kakao_account: { profile: { nickname: ' 지수 ' } } }), '지수');
  assert.equal(kakaoNickname({ kakao_account: { profile: { nickname: '가나다라마바사아자차카' } } }), '가나다라마바사아자차');
});

test('닉네임 동의가 없거나 모양이 다르면 없음', () => {
  assert.equal(kakaoNickname({ id: 1, kakao_account: { profile_nickname_needs_agreement: true } }), undefined);
  assert.equal(kakaoNickname({ kakao_account: { profile: { nickname: '   ' } } }), undefined);
  assert.equal(kakaoNickname(null), undefined);
  assert.equal(kakaoNickname({ kakao_account: { profile: { nickname: 3 } } }), undefined);
});
