// 기기 서명 확인. 앱(:core:crypto)과 같은 바이트를 만든다 — spec/op-format.md.
import { createHash, createPublicKey, verify } from 'node:crypto';

// Ed25519 공개키(32바이트)를 SPKI DER 로 감싸는 고정 접두사.
const ED25519_SPKI_PREFIX = Buffer.from('302a300506032b6570032100', 'hex');

export function verifyEd25519(message: Buffer, signature: Buffer, rawPublicKey: Buffer): boolean {
  if (rawPublicKey.length !== 32 || signature.length !== 64) return false;
  try {
    const key = createPublicKey({ key: Buffer.concat([ED25519_SPKI_PREFIX, rawPublicKey]), format: 'der', type: 'spki' });
    return verify(null, message, key, signature);
  } catch {
    return false;
  }
}

/** `u32be len ‖ bytes` */
function prefixed(b: Buffer): Buffer {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(b.length);
  return Buffer.concat([len, b]);
}

/** 앱의 Hash.of(...parts, length = 64) 와 같다(키 없는 BLAKE2b-512). */
export function blake2b512(...parts: Buffer[]): Buffer {
  const h = createHash('blake2b512');
  parts.forEach((p) => h.update(prefixed(p)));
  return h.digest();
}

/** Op 봉투 서명 대상: BLAKE2b-512( P("kkotsin-op-v1") ‖ P(header) ‖ P(ciphertext) ). */
export function opSignedBytes(header: Buffer, ciphertext: Buffer): Buffer {
  return blake2b512(Buffer.from('kkotsin-op-v1'), header, ciphertext);
}

/** 기기 교체 승인 서명 대상. cutoff 는 키 순서를 고정해 양쪽이 같은 문자열을 만든다. */
export function approvalMessage(coupleId: string, rotationId: string, newDeviceId: string, cutoff: Record<string, number>): Buffer {
  const sorted = Object.keys(cutoff).sort().map((k) => `${k}=${cutoff[k]}`).join(',');
  return Buffer.from(`kkotsin-approval-v1|${coupleId}|${rotationId}|${newDeviceId}|${sorted}`);
}

/** 새 기기가 세션 토큰을 받을 때 서명하는 챌린지. 같은 uid 의 옛 폰이 대신 받아 가지 못하게 한다. */
export function finalizeMessage(coupleId: string, rotationId: string, uid: string): Buffer {
  return Buffer.from(`kkotsin-finalize-v1|${coupleId}|${rotationId}|${uid}`);
}
