import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { approvalMessage, opSignedBytes, verifyEd25519 } from '../sig';

function keypair() {
  const { publicKey, privateKey } = generateKeyPairSync('ed25519');
  const raw = publicKey.export({ format: 'der', type: 'spki' }).subarray(-32);
  return { raw, privateKey };
}

test('Ed25519 서명을 원시 공개키로 확인한다', () => {
  const k = keypair();
  const msg = opSignedBytes(Buffer.from('header'), Buffer.from('ct'));
  const sig = sign(null, msg, k.privateKey);
  assert.equal(verifyEd25519(msg, sig, k.raw), true);
  assert.equal(verifyEd25519(opSignedBytes(Buffer.from('header2'), Buffer.from('ct')), sig, k.raw), false);
  assert.equal(verifyEd25519(msg, sig, keypair().raw), false);
  assert.equal(verifyEd25519(msg, Buffer.alloc(10), k.raw), false);
});

test('승인 메시지는 cutoff 키 순서와 무관하다', () => {
  assert.deepEqual(
    approvalMessage('c', 'r', 'd', { 'b:app': 2, 'a:app': 1 }),
    approvalMessage('c', 'r', 'd', { 'a:app': 1, 'b:app': 2 }),
  );
});

test('앱(Kotlin)이 만든 Op 봉투 서명을 서버가 같은 바이트로 확인한다', async () => {
  const { readFileSync } = await import('node:fs');
  const { join } = await import('node:path');
  const v = JSON.parse(readFileSync(join(__dirname, '../../../../spec/test-vectors/op-v1.json'), 'utf8'));
  const msg = opSignedBytes(Buffer.from(v.header, 'base64'), Buffer.from(v.ct, 'base64'));
  assert.equal(verifyEd25519(msg, Buffer.from(v.sig, 'base64'), Buffer.from(v.signPub, 'base64')), true);
});
