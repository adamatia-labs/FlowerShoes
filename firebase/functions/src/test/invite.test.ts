import { test } from 'node:test';
import assert from 'node:assert/strict';
import { acceptDecision, INVITE_TTL_MS, invitePageHtml, isInviteToken, tokenFromPath } from '../invite';

const NOW = Date.parse('2026-10-29T12:00:00Z');
const KEY = Buffer.alloc(64, 7).toString('base64');
const invite = { uid: 'jisu', deviceId: 'dev-a', pubKey: KEY, name: '지수', expireAtMs: NOW + INVITE_TTL_MS };
const accept = { uid: 'minjun', deviceId: 'dev-b', pubKey: KEY };
const free = { inviterLinked: false, joinerLinked: false };

test('초대 토큰은 base64url 22자만', () => {
  assert.equal(isInviteToken('lMUx7lUYw_FL1C-F1P9pVA'), true);
  assert.equal(isInviteToken('K7M2Q9XA'), false);
  assert.equal(isInviteToken('lMUx7lUYw_FL1C-F1P9pV/'), false);
  assert.equal(isInviteToken('lMUx7lUYw_FL1C-F1P9pVAA'), false);
});

test('경로에서 토큰을 꺼낸다(뒤 슬래시 · 쿼리 무시)', () => {
  assert.equal(tokenFromPath('/i/lMUx7lUYw_FL1C-F1P9pVA'), 'lMUx7lUYw_FL1C-F1P9pVA');
  assert.equal(tokenFromPath('/i/lMUx7lUYw_FL1C-F1P9pVA/'), 'lMUx7lUYw_FL1C-F1P9pVA');
  assert.equal(tokenFromPath('/i/../etc'), null);
  assert.equal(tokenFromPath('/x/lMUx7lUYw_FL1C-F1P9pVA'), null);
});

test('살아 있는 초대를 다른 사람이 수락하면 연결한다', () => {
  assert.equal(acceptDecision(invite, accept, NOW, free), 'link');
});

test('만료 · 자기 링크 · 이미 연결된 사람은 연결하지 않는다', () => {
  assert.equal(acceptDecision({ ...invite, expireAtMs: NOW }, accept, NOW, free), 'reject');
  assert.equal(acceptDecision(invite, { ...accept, uid: 'jisu' }, NOW, free), 'reject');
  assert.equal(acceptDecision(invite, accept, NOW, { ...free, inviterLinked: true }), 'reject');
  assert.equal(acceptDecision(invite, accept, NOW, { ...free, joinerLinked: true }), 'reject');
  assert.equal(acceptDecision(undefined, accept, NOW, free), 'reject');
  // 보낸 기기 id 로 수락하면 그 기기 문서를 덮어쓴다.
  assert.equal(acceptDecision(invite, { ...accept, deviceId: 'dev-a' }, NOW, free), 'reject');
});

test('공개키가 64바이트가 아니면 연결하지 않는다', () => {
  assert.equal(acceptDecision(invite, { ...accept, pubKey: Buffer.alloc(32).toString('base64') }, NOW, free), 'reject');
  assert.equal(acceptDecision({ ...invite, pubKey: 'not base64!' }, accept, NOW, free), 'reject');
});

const stores = { play: 'https://play.google.com/store/apps/details?id=com.sadturtleman.flowershoes', appStore: 'https://apps.apple.com/app/idTODO' };

test('OG 페이지는 보낸 사람 이름만 싣고 스토어 버튼 · 다시 누르기 안내를 보여 준다', () => {
  const html = invitePageHtml({ name: '지수', expired: false, url: 'https://x.web.app/i/t', stores });
  assert.match(html, /<meta property="og:title" content="지수님이 꽃신에 초대했어요">/);
  assert.match(html, /설치 후 이 링크를 다시 눌러 주세요/);
  assert.ok(html.includes(stores.play) && html.includes(stores.appStore));
  assert.doesNotMatch(html, /dev-a|jisu/);
});

test('만료 · 없는 초대는 같은 주소에서 만료 문구, OG 도 중립', () => {
  const html = invitePageHtml({ name: undefined, expired: true, url: 'https://x.web.app/i/t', stores });
  assert.match(html, /이 초대 링크는 만료됐어요/);
  assert.doesNotMatch(html, /님이 꽃신에 초대했어요/);
});

test('이름은 HTML 로 이스케이프한다', () => {
  const html = invitePageHtml({ name: '<script>"&', expired: false, url: 'https://x.web.app/i/t', stores });
  assert.ok(!html.includes('<script>"&'));
  assert.ok(html.includes('&lt;script&gt;&quot;&amp;'));
});

test('이름이 비면 중립 제목', () => {
  const html = invitePageHtml({ name: '', expired: false, url: 'https://x.web.app/i/t', stores });
  assert.match(html, /연인이 꽃신에 초대했어요/);
});
