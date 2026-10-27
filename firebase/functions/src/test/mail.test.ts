import { test } from 'node:test';
import assert from 'node:assert/strict';
import { backupWarningId, Delivery, deliverMail, MAIL_LEASE_MS, mailFailure, mailMessageId, MailStore, MAX_MAIL_ATTEMPTS, nextMailClaim, parseMail } from '../mail';
import { smtpOptions } from '../mail-smtp';

const message = { to: 'recipient@example.test', message: { subject: '백업 안내', text: '앱을 열면 계속 보관돼요.' } };

function memoryStore(data: unknown = message, initial?: Delivery) {
  let delivery = initial;
  const store: MailStore = {
    async claim(now, claimId) {
      const next = nextMailClaim(delivery, now, claimId);
      if (typeof next === 'string') return next;
      delivery = next;
      return { data, delivery };
    },
    async finish(claimId, next) {
      if (delivery?.claimId !== claimId || delivery.state !== 'PROCESSING') return false;
      delivery = next;
      return true;
    },
  };
  return { store, state: () => delivery! };
}

test('메일: 수신자 한 명·평문만 전달하고 임의 첨부·헤더를 무시한다', () => {
  assert.deepEqual(parseMail({ ...message, from: 'evil@example.test', cc: 'other@example.test',
    message: { ...message.message, html: '<img src="file:///etc/passwd">', attachments: [{ path: '/etc/passwd' }] },
  }), message);
  for (const to of ['a@b.test,second@b.test', 'a@b.test\r\nBcc: x@y.test', ['a@b.test'], 'bad']) {
    assert.equal(parseMail({ ...message, to }), undefined);
  }
  assert.equal(parseMail({ ...message, message: { ...message.message, subject: 'x\r\nBcc: x@y.test' } }), undefined);
  assert.equal(parseMail({ ...message, message: { subject: 'x', text: 'a'.repeat(20_001) } }), undefined);
  assert.equal(parseMail(null), undefined);
});

test('메일: 완료한 문서 이벤트를 재전달해도 한 번만 발송한다', async () => {
  const m = memoryStore();
  let sends = 0;
  const send = async () => { sends++; };
  assert.equal(await deliverMail(m.store, send), 'sent');
  assert.equal(await deliverMail(m.store, send), 'skipped');
  assert.equal(sends, 1);
  assert.equal(m.state().state, 'SUCCESS');
  assert.ok(m.state().sentAtMs);
});

test('메일: 동시에 전달된 이벤트는 실행 중 lease를 빼앗지 않는다', async () => {
  const m = memoryStore();
  let release!: () => void;
  const wait = new Promise<void>(r => { release = r; });
  let sends = 0;
  const first = deliverMail(m.store, async () => { sends++; await wait; });
  await assert.rejects(deliverMail(m.store, async () => { sends++; }), /MAIL_LEASE_BUSY/);
  release();
  await first;
  assert.equal(sends, 1);
});

test('메일: 임시 SMTP 오류를 재시도하고 성공 상태로 바꾼다', async () => {
  const m = memoryStore();
  await assert.rejects(deliverMail(m.store, async () => { throw { responseCode: 451, message: 'secret' }; }), /MAIL_RETRY/);
  assert.equal(m.state().state, 'RETRY');
  assert.equal(m.state().errorCode, 'SMTP_TEMPORARY');
  assert.equal(await deliverMail(m.store, async () => {}), 'sent');
  assert.equal(m.state().attempts, 2);
  assert.equal(m.state().errorCode, undefined);
  assert.ok(!JSON.stringify(m.state()).includes('secret'));
});

test('메일: 반복 장애는 5회 뒤 ERROR로 종료한다', async () => {
  const m = memoryStore();
  let sends = 0;
  const send = async () => { sends++; throw { code: 'ETIMEDOUT' }; };
  for (let i = 1; i < MAX_MAIL_ATTEMPTS; i++) await assert.rejects(deliverMail(m.store, send), /MAIL_RETRY/);
  assert.equal(await deliverMail(m.store, send), 'failed');
  assert.equal(await deliverMail(m.store, send), 'skipped');
  assert.equal(sends, MAX_MAIL_ATTEMPTS);
  assert.equal(m.state().state, 'ERROR');
});

test('메일: 인증/영구 거부/잘못된 메시지는 반복 발송하지 않는다', async () => {
  for (const error of [{ code: 'EAUTH' }, { responseCode: 550 }, { code: 'MAIL_CONFIG' }]) {
    const m = memoryStore();
    assert.equal(await deliverMail(m.store, async () => { throw error; }), 'failed');
    assert.equal(m.state().state, 'ERROR');
  }
  const m = memoryStore({ to: 'bad' });
  assert.equal(await deliverMail(m.store, async () => { assert.fail('잘못된 문서는 보내면 안 된다'); }), 'failed');
  assert.equal(m.state().errorCode, 'INVALID_MESSAGE');
  assert.deepEqual(mailFailure({ code: 'EAUTH', responseCode: 454 }), { retry: true, code: 'SMTP_TEMPORARY' });
});

test('메일: 죽은 프로세스의 lease는 만료 후 회수하고 횟수를 제한한다', () => {
  const first = nextMailClaim(undefined, 1_000, 'first') as Delivery;
  assert.equal(nextMailClaim(first, 1_001, 'second'), 'busy');
  const second = nextMailClaim(first, 1_000 + MAIL_LEASE_MS, 'second') as Delivery;
  assert.equal(second.claimId, 'second');
  assert.equal(second.attempts, 2);
  const last = nextMailClaim({ ...first, attempts: MAX_MAIL_ATTEMPTS }, 1_000 + MAIL_LEASE_MS, 'last') as Delivery;
  assert.equal(last.state, 'ERROR');
});

test('메일: SMTP 수락 뒤 상태 저장 실패는 성공으로 보고하지 않는다', async () => {
  const m = memoryStore();
  m.store.finish = async () => { throw new Error('db unavailable'); };
  await assert.rejects(deliverMail(m.store, async () => {}), /db unavailable/);
  assert.equal(m.state().state, 'PROCESSING');
});

test('메일: 경고 ID와 Message-ID는 같은 요청에서 고정되고 다른 기간/프로젝트와 구별된다', () => {
  assert.equal(backupWarningId('uid', 1, 7), backupWarningId('uid', 1, 7));
  assert.notEqual(backupWarningId('uid', 1, 7), backupWarningId('uid', 2, 7));
  assert.notEqual(backupWarningId('uid', 1, 7), backupWarningId('uid', 1, 30));
  assert.equal(mailMessageId('p1', 'doc', 'a@example.test'), mailMessageId('p1', 'doc', 'a@example.test'));
  assert.notEqual(mailMessageId('p1', 'doc', 'a@example.test'), mailMessageId('p2', 'doc', 'a@example.test'));
});

test('SMTP: TLS와 인증·타임아웃을 강제하고 파일/URL/디버그 접근을 막는다', () => {
  const config = { host: 'smtp.gmail.com', port: 465, user: 'a@example.test', from: 'a@example.test', password: 'fake-test-only' };
  const options = smtpOptions(config);
  assert.equal(options.secure, true);
  assert.equal(options.requireTLS, true);
  assert.equal(options.tls?.rejectUnauthorized, true);
  assert.equal(options.disableFileAccess, true);
  assert.equal(options.disableUrlAccess, true);
  assert.equal(options.debug, false);
  assert.equal(smtpOptions({ ...config, port: 587 }).secure, false);
  assert.throws(() => smtpOptions({ ...config, port: 25 }), /MAIL_CONFIG/);
  assert.throws(() => smtpOptions({ ...config, password: '' }), /MAIL_CONFIG/);
});
