import { test } from 'node:test';
import assert from 'node:assert/strict';
import { kakaoProviderId } from '../account-lifecycle';

test('카카오 연결 해제는 재가입 세대가 붙은 UID에서도 원래 공급자 ID를 사용한다', () => {
  assert.equal(kakaoProviderId('kakao:123'), '123');
  assert.equal(kakaoProviderId('kakao:123:2c46f08d-552a-4721-8d3d-956001e041a7'), '123');
  assert.equal(kakaoProviderId('apple-user'), undefined);
  assert.equal(kakaoProviderId('kakao:123/other'), undefined);
});
