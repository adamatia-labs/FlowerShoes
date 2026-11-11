import UserNotifications
import Foundation

/// Notification Service Extension(설계 6-6).
///
/// - iOS 는 여기서 알림을 숨길 수 없다(필터링 권한은 신청하지 않음) → 보낼지 · 묶을지는 서버가 이미 정했다.
/// - 푸시에 인라인으로 실린 작은 암호문(원본 ≤ 2KB)만 풀어 문구를 바꾸고, 받은 Op 대기열 · 위젯 스냅샷에 반영한다.
/// - 본 DB 는 건드리지 않는다. 그림 · 편지처럼 큰 것은 kind 만 보고 기본 문구를 쓰고, 내용은 앱을 열 때 가져온다.
/// - Kotlin 프레임워크 · Firebase SDK 를 싣지 않는다(메모리 한도 약 24MB).
final class NotificationService: UNNotificationServiceExtension {

    override func didReceive(_ request: UNNotificationRequest, withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        let content = (request.content.mutableCopy() as? UNMutableNotificationContent) ?? UNMutableNotificationContent()
        let info = request.content.userInfo
        var delivered = false
        defer { if !delivered { contentHandler(content) } }

        guard (info["v"] as? String) == "1", let kind = info["kind"] as? String else {
            // 암호화 Op가 아닌 푸시는 서버가 보낸 안내 문구를 유지한다.
            return
        }

        // 모르는 kind 는 서버 문구 그대로. missyou · status 만 인라인을 해석한다.
        guard let linkData = try? Data(contentsOf: SharedStore.container.appendingPathComponent("link.json")),
              let link = (try? JSONSerialization.jsonObject(with: linkData)) as? [String: Any] else { return }
        let scope = link["keyScope"] as? String
        let snapshotOwner = (link["snapshotOwner"] as? [String: Any]).flatMap {
            try? JSONDecoder().decode(SharedStore.WidgetOwner.self, from: JSONSerialization.data(withJSONObject: $0))
        }
        guard kind == "missyou" || kind == "status",
              let ctB64 = info["ct"] as? String, let ct = Data(base64Encoded: ctB64),
              let sigB64 = info["sig"] as? String, let sig = Data(base64Encoded: sigB64),
              let headerB64 = info["header"] as? String, let header = Data(base64Encoded: headerB64),
              let parsed = OpEnvelope.Header.parse([UInt8](header)),
              let secrets = SharedStore.readSecrets(scope: scope),
              let senderPub = secrets.secret("partner.signPub"),
              let coupleKey = secrets.secret("coupleKey.\(parsed.keyEpoch)"),
              let plain = OpEnvelope.open(header: [UInt8](header), ciphertext: [UInt8](ct), signature: [UInt8](sig), signPub: senderPub, coupleKey: coupleKey),
              let split = OpPlain.split(plain)
        else { return }
        let payload = split.payload
        guard (try? Data(contentsOf: SharedStore.container.appendingPathComponent("link.json"))) == linkData else { return }

        // 대기열과 미리보기 전달도 스냅샷 소유권을 확인한 같은 잠금 안에서만 발행한다.
        let queued: [String: Any] = ["opId": parsed.opId, "stream": parsed.stream, "seq": parsed.seq, "header": headerB64, "ct": ctB64, "sig": sigB64, "kind": kind]
        let queuedData = try? JSONSerialization.data(withJSONObject: queued)
        let json = (try? JSONSerialization.jsonObject(with: Data(payload))) as? [String: Any]
        let put = json?["status"] as? [String: Any]
        let status = kind == "status" ? put?["label"] as? String : nil
        SharedStore.updateSnapshot(owner: snapshotOwner, onUpdated: {
            if let queuedData { SharedStore.enqueueReceived(opId: parsed.opId, json: queuedData) }
            if let status {
                // 설정 › 알림 › 미리보기 숨기기: 문구는 서버 기본 그대로(위젯만 갱신).
                let hidden = FileManager.default.fileExists(atPath: SharedStore.container.appendingPathComponent("prefs/hide_preview").path)
                if !hidden { content.body = "상태: \(status)" }
            }
            delivered = true
            contentHandler(content)
        }) { snapshot in
            if kind == "missyou" { snapshot.missYouFromPartnerToday += 1 }
            if let status {
                snapshot.partnerStatus = status
                snapshot.partnerStatusColor = (put?["color"] as? NSNumber)?.int64Value
                // 끝나는 시각 문구는 앱이 다음에 스냅샷을 다시 쓸 때 채운다.
                snapshot.partnerStatusUntil = nil
            }
        }
    }

    override func serviceExtensionTimeWillExpire() {
        // 시간이 다 되면 서버가 보낸 기본 문구 그대로 둔다.
    }
}
