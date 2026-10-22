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
        defer { contentHandler(content) }

        guard (info["v"] as? String) == "1", let kind = info["kind"] as? String else {
            content.body = "새 소식이 있어요"
            return
        }

        // 모르는 kind 는 서버 문구 그대로. missyou · status 만 인라인을 해석한다.
        guard kind == "missyou" || kind == "status",
              let ctB64 = info["ct"] as? String, let ct = Data(base64Encoded: ctB64),
              let sigB64 = info["sig"] as? String, let sig = Data(base64Encoded: sigB64),
              let headerB64 = info["header"] as? String, let header = Data(base64Encoded: headerB64),
              let parsed = OpEnvelope.Header.parse([UInt8](header)),
              let senderPub = SharedStore.secret("partner.signPub"),
              let coupleKey = SharedStore.secret("coupleKey.\(parsed.keyEpoch)"),
              let plain = OpEnvelope.open(header: [UInt8](header), ciphertext: [UInt8](ct), signature: [UInt8](sig), signPub: senderPub, coupleKey: coupleKey),
              let split = OpPlain.split(plain)
        else { return }
        let payload = split.payload

        // 앱이 다음에 열릴 때 DB 에 합칠 수 있게 대기열에 둔다(중복은 (stream, seq) 로 앱이 거른다).
        let queued: [String: Any] = ["opId": parsed.opId, "stream": parsed.stream, "seq": parsed.seq, "header": headerB64, "ct": ctB64, "sig": sigB64, "kind": kind]
        if let data = try? JSONSerialization.data(withJSONObject: queued) {
            SharedStore.enqueueReceived(opId: parsed.opId, json: data)
        }

        let json = (try? JSONSerialization.jsonObject(with: Data(payload))) as? [String: Any]
        switch kind {
        case "missyou":
            SharedStore.updateSnapshot { $0.missYouFromPartnerToday += 1 }
        case "status":
            if let status = (json?["status"] as? [String: Any])?["label"] as? String {
                content.body = "상태: \(status)"
                SharedStore.updateSnapshot { $0.partnerStatus = status }
            }
        default:
            break
        }
    }

    override func serviceExtensionTimeWillExpire() {
        // 시간이 다 되면 서버가 보낸 기본 문구 그대로 둔다.
    }
}
