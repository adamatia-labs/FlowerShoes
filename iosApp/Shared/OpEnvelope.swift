import Foundation
import Sodium

/// Op 봉투(spec/op-format.md)의 Swift 구현. Kotlin `:core:crypto` 와 같은 바이트를 만들고 읽는다.
/// NSE · 위젯이 쓴다 — 둘 다 Kotlin 프레임워크를 싣지 않는다(NSE 메모리 한도 약 24MB).
enum OpEnvelope {
    struct Header: Equatable {
        let opId: String
        let stream: String
        let seq: Int64
        let keyEpoch: Int32

        func bytes() -> [UInt8] {
            var out: [UInt8] = []
            out += prefixed(Array(opId.utf8))
            out += prefixed(Array(stream.utf8))
            out += be64(UInt64(bitPattern: seq))
            out += be32(UInt32(bitPattern: keyEpoch))
            return out
        }

        static func parse(_ b: [UInt8]) -> Header? {
            var pos = 0
            func int32() -> UInt32? {
                guard pos + 4 <= b.count else { return nil }
                defer { pos += 4 }
                return b[pos..<pos + 4].reduce(0) { ($0 << 8) | UInt32($1) }
            }
            func str() -> String? {
                guard let n = int32(), pos + Int(n) <= b.count else { return nil }
                defer { pos += Int(n) }
                return String(bytes: b[pos..<pos + Int(n)], encoding: .utf8)
            }
            guard let opId = str(), let stream = str(), let hi = int32(), let lo = int32(), let epoch = int32() else { return nil }
            return Header(opId: opId, stream: stream, seq: Int64(bitPattern: (UInt64(hi) << 32) | UInt64(lo)), keyEpoch: Int32(bitPattern: epoch))
        }
    }

    private static let sodium = Sodium()

    /// 서명 대상: BLAKE2b-512( P("kkotsin-op-v1") ‖ P(header) ‖ P(ciphertext) )
    static func signedBytes(header: [UInt8], ciphertext: [UInt8]) -> [UInt8]? {
        let joined = prefixed(Array("kkotsin-op-v1".utf8)) + prefixed(header) + prefixed(ciphertext)
        return sodium.genericHash.hash(message: joined, outputLength: 64)
    }

    static func verify(header: [UInt8], ciphertext: [UInt8], signature: [UInt8], signPub: [UInt8]) -> Bool {
        guard let msg = signedBytes(header: header, ciphertext: ciphertext) else { return false }
        return sodium.sign.verify(message: msg, publicKey: signPub, signature: signature)
    }

    /// 서명을 먼저 확인하고 푼다. 암호문 형식은 `nonce(24) ‖ ciphertext ‖ tag(16)`, AAD 는 머리 바이트.
    static func open(header: [UInt8], ciphertext: [UInt8], signature: [UInt8], signPub: [UInt8], coupleKey: [UInt8]) -> [UInt8]? {
        guard verify(header: header, ciphertext: ciphertext, signature: signature, signPub: signPub) else { return nil }
        return sodium.aead.xchacha20poly1305ietf.decrypt(nonceAndAuthenticatedCipherText: ciphertext, secretKey: coupleKey, additionalData: header)
    }

    /// 위젯 전송용: 암호화 · 서명. 반환 = (암호문, 서명).
    static func seal(header: Header, plaintext: [UInt8], coupleKey: [UInt8], signSecret: [UInt8]) -> (ct: [UInt8], sig: [UInt8])? {
        let h = header.bytes()
        guard let ct: [UInt8] = sodium.aead.xchacha20poly1305ietf.encrypt(message: plaintext, secretKey: coupleKey, additionalData: h),
              let msg = signedBytes(header: h, ciphertext: ct),
              let sig = sodium.sign.signature(message: msg, secretKey: signSecret) else { return nil }
        return (ct, sig)
    }
}

/// Op 평문 = lamport(8바이트 big-endian) ‖ 본문(JSON).
enum OpPlain {
    static func split(_ plain: [UInt8]) -> (lamport: Int64, payload: [UInt8])? {
        guard plain.count >= 8 else { return nil }
        let l = plain[0..<8].reduce(UInt64(0)) { ($0 << 8) | UInt64($1) }
        return (Int64(bitPattern: l), Array(plain[8...]))
    }

    static func join(lamport: Int64, payload: [UInt8]) -> [UInt8] { be64(UInt64(bitPattern: lamport)) + payload }
}

func prefixed(_ b: [UInt8]) -> [UInt8] { be32(UInt32(b.count)) + b }
func be32(_ v: UInt32) -> [UInt8] { (0..<4).map { UInt8(truncatingIfNeeded: v >> (24 - 8 * $0)) } }
func be64(_ v: UInt64) -> [UInt8] { (0..<8).map { UInt8(truncatingIfNeeded: v >> (56 - 8 * $0)) } }
