import Foundation

/// 위젯의 보고싶어 전송(설계 6-6). Firebase SDK 없이 기기 서명으로 함수에 보낸다.
enum WidgetSender {
    enum Failure: Error { case notLinked, crypto, rejected(Int) }

    /// Cloud Function 엔드포인트(서울 리전). 프로젝트 id 는 빌드 설정으로 바꾼다.

    static func sendMissYou() async throws {
        guard let config = linkConfig(),
              let coupleKey = SharedStore.secret("coupleKey.\(config.epoch)", scope: config.keyScope),
              let signSecret = SharedStore.secret("device.signSecret", scope: config.keyScope) else { throw Failure.notLinked }

        _ = try WidgetSendJournal(root: SharedStore.container).enqueue { seq in
            guard linkConfig() == config else { throw Failure.notLinked }
            let opId = UUID().uuidString.lowercased()
            let header = OpEnvelope.Header(opId: opId, stream: "\(config.deviceId):widget", seq: seq, keyEpoch: config.epoch)
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let payload = #"{"t":"missyou","missYou":{"id":"\#(opId)","authorId":"\#(config.userId)","sentAt":"\#(ISO8601DateFormatter().string(from: Date()))"}}"#
            let plain = OpPlain.join(lamport: now, payload: Array(payload.utf8))
            guard let sealed = OpEnvelope.seal(header: header, plaintext: plain, coupleKey: coupleKey, signSecret: signSecret) else { throw Failure.crypto }
            let body = try JSONSerialization.data(withJSONObject: [
                "coupleId": config.coupleId, "fromDeviceId": config.deviceId, "toDeviceId": config.partnerDeviceId,
                "opId": opId, "kind": "missyou",
                "header": Data(header.bytes()).base64EncodedString(),
                "ct": Data(sealed.ct).base64EncodedString(),
                "sig": Data(sealed.sig).base64EncodedString(),
            ])
            return WidgetSendJournal.Envelope(opId: opId, body: body)
        }
        guard linkConfig() == config else { throw Failure.notLinked }
        guard let address = config.sendEndpoint, let endpoint = URL(string: address) else { throw Failure.notLinked }
        let destination = WidgetOutboxDelivery.Destination(coupleId: config.coupleId, deviceId: config.deviceId, partnerDeviceId: config.partnerDeviceId)
        try await WidgetOutboxDelivery.send(root: SharedStore.container, destination: destination) { body in
            guard linkConfig() == config else { throw Failure.notLinked }
            var req = URLRequest(url: endpoint)
            req.timeoutInterval = 10
            req.httpMethod = "POST"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = body
            let (_, response) = try await URLSession.shared.data(for: req)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            // 실패해도 사본이 남아 있어 다음 위젯 전송 또는 앱 실행 때 다시 보낸다.
            if code != 200 && code != 409 { throw Failure.rejected(code) }
        }
    }

    struct LinkConfig: Codable, Equatable {
        let coupleId: String
        let userId: String
        let deviceId: String
        let partnerDeviceId: String
        let epoch: Int32
        let sendEndpoint: String?
        var keyScope: String? = nil
    }

    /// 앱이 연결 · 키 교체 때 App Group 에 써 둔다.
    static func linkConfig() -> LinkConfig? {
        guard let data = try? Data(contentsOf: SharedStore.container.appendingPathComponent("link.json")) else { return nil }
        return try? JSONDecoder().decode(LinkConfig.self, from: data)
    }

}
