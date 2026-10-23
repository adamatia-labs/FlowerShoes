import Foundation

/// 위젯의 보고싶어 전송(설계 6-6). Firebase SDK 없이 기기 서명으로 함수에 보낸다.
enum WidgetSender {
    enum Failure: Error { case notLinked, crypto, rejected(Int) }

    /// Cloud Function 엔드포인트(서울 리전). 프로젝트 id 는 빌드 설정으로 바꾼다.
    static let endpoint = URL(string: "https://asia-northeast3-kkotsin-dev.cloudfunctions.net/sendFromWidget")!

    static func sendMissYou() async throws {
        guard let config = linkConfig(),
              let coupleKey = SharedStore.secret("coupleKey.\(config.epoch)"),
              let signSecret = SharedStore.secret("device.signSecret") else { throw Failure.notLinked }

        let seq = nextSeq()
        let opId = UUID().uuidString.lowercased()
        let header = OpEnvelope.Header(opId: opId, stream: "\(config.deviceId):widget", seq: seq, keyEpoch: config.epoch)
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        // :core:data OpPayload.MissYouPut 과 같은 JSON
        let payload = #"{"t":"missyou","missYou":{"id":"\#(opId)","authorId":"\#(config.userId)","sentAt":"\#(ISO8601DateFormatter().string(from: Date()))"}}"#
        let plain = OpPlain.join(lamport: now, payload: Array(payload.utf8))
        guard let sealed = OpEnvelope.seal(header: header, plaintext: plain, coupleKey: coupleKey, signSecret: signSecret) else { throw Failure.crypto }

        // 재동기화용 평문 사본 — 앱이 실행되면 SyncOp 로그(stream=widget)로 가져가고 지운다.
        SharedStore.writeProtected(Data(plain), to: SharedStore.container.appendingPathComponent("widget-outbox/\(seq).op"))

        var req = URLRequest(url: endpoint)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: [
            "coupleId": config.coupleId, "fromDeviceId": config.deviceId, "toDeviceId": config.partnerDeviceId,
            "opId": opId, "kind": "missyou",
            "header": Data(header.bytes()).base64EncodedString(),
            "ct": Data(sealed.ct).base64EncodedString(),
            "sig": Data(sealed.sig).base64EncodedString(),
        ])
        let (_, response) = try await URLSession.shared.data(for: req)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        // 실패해도 사본이 남아 있어 앱이 다음 실행 때 보낸다.
        if code != 200 && code != 409 { throw Failure.rejected(code) }
    }

    struct LinkConfig: Codable {
        let coupleId: String
        let userId: String
        let deviceId: String
        let partnerDeviceId: String
        let epoch: Int32
    }

    /// 앱이 연결 · 키 교체 때 App Group 에 써 둔다.
    static func linkConfig() -> LinkConfig? {
        guard let data = try? Data(contentsOf: SharedStore.container.appendingPathComponent("link.json")) else { return nil }
        return try? JSONDecoder().decode(LinkConfig.self, from: data)
    }

    /// App Intent 가 동시에 여러 개 돌아도 번호가 겹치거나 빠지지 않게 파일 잠금 안에서 1 올린다.
    static func nextSeq() -> Int64 {
        let url = SharedStore.container.appendingPathComponent("widget-seq")
        var result: Int64 = 1
        NSFileCoordinator().coordinate(writingItemAt: url, options: .forReplacing, error: nil) { u in
            let fd = open(u.path, O_RDWR | O_CREAT, 0o600)
            guard fd >= 0 else { return }
            defer { close(fd) }
            flock(fd, LOCK_EX)
            defer { flock(fd, LOCK_UN) }
            var buf = [UInt8](repeating: 0, count: 32)
            let n = read(fd, &buf, 32)
            let current = n > 0 ? Int64(String(bytes: buf[0..<n], encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "0") ?? 0 : 0
            result = current + 1
            let text = Array("\(result)".utf8)
            ftruncate(fd, 0)
            lseek(fd, 0, SEEK_SET)
            _ = text.withUnsafeBufferPointer { write(fd, $0.baseAddress, text.count) }
        }
        return result
    }
}
