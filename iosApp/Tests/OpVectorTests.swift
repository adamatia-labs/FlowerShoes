import XCTest
import Sodium

/// 공유 테스트 벡터(설계 6-8). Kotlin 이 만든 spec/test-vectors/op-v1.json 을 Swift 가 같은 바이트로 검증한다.
final class OpVectorTests: XCTestCase {

    private func vector() throws -> [String: Any] {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("spec/test-vectors/op-v1.json")
        return try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [String: Any]
    }

    private func bytes(_ v: [String: Any], _ key: String) -> [UInt8] { [UInt8](Data(base64Encoded: v[key] as! String)!) }

    func testHeaderBytesMatchKotlin() throws {
        let v = try vector()
        let header = OpEnvelope.Header(opId: "op-vector-1", stream: "dev-a:widget", seq: 7, keyEpoch: 2)
        XCTAssertEqual(header.bytes(), bytes(v, "header"))
        XCTAssertEqual(OpEnvelope.Header.parse(bytes(v, "header")), header)
    }

    func testOpensKotlinEnvelope() throws {
        let v = try vector()
        let plain = OpEnvelope.open(header: bytes(v, "header"), ciphertext: bytes(v, "ct"), signature: bytes(v, "sig"),
                                    signPub: bytes(v, "signPub"), coupleKey: bytes(v, "coupleKey"))
        XCTAssertEqual(plain.flatMap { String(bytes: $0, encoding: .utf8) }, v["plaintext"] as? String)
    }

    func testRejectsTamperedCiphertext() throws {
        let v = try vector()
        var ct = bytes(v, "ct")
        ct[ct.count - 1] ^= 1
        XCTAssertNil(OpEnvelope.open(header: bytes(v, "header"), ciphertext: ct, signature: bytes(v, "sig"),
                                     signPub: bytes(v, "signPub"), coupleKey: bytes(v, "coupleKey")))
    }
}
