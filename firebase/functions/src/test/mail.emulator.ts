// 명시적인 로컬 demo 프로젝트에서만 실제 Firestore transaction + TLS SMTP를 실행한다.
import { test, before, after, mock } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { initializeApp, deleteApp } from 'firebase-admin/app';
import { getFirestore, QueryDocumentSnapshot } from 'firebase-admin/firestore';
import nodemailer from 'nodemailer';
import SMTPTransport from 'nodemailer/lib/smtp-transport';
import { SMTPServer } from 'smtp-server';
import { onMailCreated } from '../mail-function';
import { MAIL_LEASE_MS } from '../mail';

if (!process.env.FIRESTORE_EMULATOR_HOST || process.env.GCLOUD_PROJECT !== 'demo-kkotsin-mail') {
  throw new Error('Run npm run test:mail:emulator with the local demo project only');
}
const app = initializeApp({ projectId: 'demo-kkotsin-mail' });
const db = getFirestore(app);
const dir = mkdtempSync(join(tmpdir(), 'kkotsin-smtp-test-'));
let server: SMTPServer;
const received: string[] = [];
let rejectCode = 0;
let authenticated = 0;
const payload = { to: 'recipient@example.test', message: { subject: 'Local SMTP test', text: 'Backup warning test only.' } };

before(async () => {
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
    '-keyout', join(dir, 'key.pem'), '-out', join(dir, 'cert.pem'), '-subj', '/CN=localhost',
    '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'], { stdio: 'ignore' });
  const cert = readFileSync(join(dir, 'cert.pem'));
  server = new SMTPServer({
    secure: true, key: readFileSync(join(dir, 'key.pem')), cert, logger: false,
    onAuth(auth, _session, callback) {
      if (auth.username !== 'sender@example.test' || auth.password !== 'local-fake-password') return callback(new Error('test auth mismatch'));
      authenticated++;
      callback(null, { user: auth.username });
    },
    onData(stream, _session, callback) {
      let raw = '';
      stream.on('data', chunk => { raw += chunk.toString(); });
      stream.on('end', () => {
        if (rejectCode) return callback(Object.assign(new Error('SMTP test refusal'), { responseCode: rejectCode }));
        received.push(raw);
        callback(null);
      });
    },
  });
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = (server.server.address() as { port: number }).port;
  const realTransport = nodemailer.createTransport.bind(nodemailer);
  // 운영 설정(465/TLS)은 그대로 검증하고 테스트에서만 임시 localhost 포트·인증서를 주입한다.
  mock.method(nodemailer, 'createTransport', (options: SMTPTransport.Options) => {
    assert.equal(options.host, 'localhost');
    assert.equal(options.secure, true);
    assert.equal(options.tls?.rejectUnauthorized, true);
    return realTransport({ ...options, port, tls: { ...options.tls, ca: cert } });
  });
  process.env.SMTP_HOST = 'localhost';
  process.env.SMTP_PORT = '465';
  process.env.SMTP_USER = 'sender@example.test';
  process.env.SMTP_FROM = 'sender@example.test';
  process.env.SMTP_PASSWORD = 'local-fake-password';
});

after(async () => {
  mock.restoreAll();
  if (server) await new Promise<void>(resolve => server.close(resolve));
  await db.terminate();
  await deleteApp(app);
  rmSync(dir, { recursive: true, force: true });
});

async function run(id: string) {
  const data = await db.doc(`mail/${id}`).get();
  assert.ok(data.exists);
  await onMailCreated.run({
    data: data as QueryDocumentSnapshot, params: { mailId: id }, project: 'demo-kkotsin-mail',
    id: `test-${id}`, time: new Date().toISOString(), location: 'asia-northeast3',
    database: '(default)', namespace: '(default)', document: `mail/${id}`,
    specversion: '1.0', type: 'google.cloud.firestore.document.v1.created', source: 'local-test',
  });
}

test('Firestore → 함수 → TLS 인증 SMTP 실제 수신, 완료 이벤트 재전달은 미발송', async () => {
  const ref = db.doc('mail/success');
  await ref.set(payload);
  const start = received.length;
  await run(ref.id);
  await run(ref.id);
  assert.equal(received.length, start + 1);
  assert.match(received.at(-1)!, /To: recipient@example.test/);
  assert.match(received.at(-1)!, /Backup warning test only/);
  assert.ok(authenticated > 0);
  assert.equal((await ref.get()).get('delivery.state'), 'SUCCESS');
  assert.equal((await ref.get()).get('delivery.attempts'), 1);
});

test('Firestore transaction이 동시 실행 2개를 하나의 SMTP 전송으로 직렬화한다', async () => {
  const ref = db.doc('mail/concurrent');
  await ref.set(payload);
  const start = received.length;
  const results = await Promise.allSettled([run(ref.id), run(ref.id)]);
  assert.ok(results.some(r => r.status === 'fulfilled'));
  await run(ref.id);
  assert.equal(received.length, start + 1);
  assert.equal((await ref.get()).get('delivery.state'), 'SUCCESS');
});

test('SMTP 451 → RETRY → 정상 재시도 SUCCESS, 원본 오류는 저장하지 않는다', async () => {
  const ref = db.doc('mail/retry');
  await ref.set(payload);
  rejectCode = 451;
  await assert.rejects(run(ref.id), /MAIL_DELIVERY_RETRY/);
  assert.equal((await ref.get()).get('delivery.state'), 'RETRY');
  rejectCode = 0;
  await run(ref.id);
  assert.equal((await ref.get()).get('delivery.state'), 'SUCCESS');
  assert.equal((await ref.get()).get('delivery.attempts'), 2);
});

test('SMTP 550은 ERROR로 남고 재전달해도 재발송하지 않는다', async () => {
  const ref = db.doc('mail/permanent');
  await ref.set(payload);
  rejectCode = 550;
  await run(ref.id);
  rejectCode = 0;
  const start = received.length;
  await run(ref.id);
  assert.equal(received.length, start);
  assert.equal((await ref.get()).get('delivery.state'), 'ERROR');
  assert.equal((await ref.get()).get('delivery.errorCode'), 'SMTP_REJECTED');
});

test('만료 lease를 회수하고 잘못된 수신자는 SMTP 연결 없이 거부한다', async () => {
  const ref = db.doc('mail/expired');
  await ref.set({ ...payload, delivery: {
    state: 'PROCESSING', attempts: 1, claimId: 'dead', leaseUntilMs: Date.now() - MAIL_LEASE_MS,
  } });
  await run(ref.id);
  assert.equal((await ref.get()).get('delivery.state'), 'SUCCESS');
  const bad = db.doc('mail/invalid');
  await bad.set({ ...payload, to: 'bad\r\nBcc: hidden@example.test' });
  const start = authenticated;
  await run(bad.id);
  assert.equal(authenticated, start);
  assert.equal((await bad.get()).get('delivery.errorCode'), 'INVALID_MESSAGE');
});
