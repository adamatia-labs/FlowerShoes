import Foundation
import Security

/// 앱 · NSE · 위젯이 함께 쓰는 저장소.
/// - App Group: 위젯 스냅샷(JSON), 받은 Op 대기열(Op 1개 = 파일 1개), 위젯이 보낸 Op 사본.
///   파일 보호 등급은 completeUntilFirstUserAuthentication — 평문 Op 이 들어 있다. 백업에서 뺀다.
/// - Keychain(access group 공유, ThisDeviceOnly): 기기 키 · 커플 키. iCloud 키체인으로 동기화하지 않는다.
///
/// 본 DB 는 App Group 에 두지 않는다 — 앱이 잠금을 잡은 채 백그라운드로 가면 0xdead10cc 로 강제 종료된다.
enum SharedStore {
    static let appGroup = "group.com.sadturtleman.kkotsin"
    static let keychainGroup = "com.sadturtleman.kkotsin.shared"

    static var container: URL {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)!
    }

    // MARK: 위젯 스냅샷 — :core:widget-bridge WidgetSnapshot 과 같은 필드

    struct WidgetSnapshot: Codable {
        var version: Int
        var theme: String
        var daysLeft: Int?
        var dischargeDate: String?
        var progress: Double?
        var rank: String?
        var nextEventTitle: String?
        var nextEventDays: Int?
        var partnerStatus: String?
        var missYouFromPartnerToday: Int
        var linked: Bool
        var drawingDocPath: String?
        var drawingPngPath: String?
        var drawingShape: String?
        var drawingLetterId: String?
        var drawingId: String?
        /// 받은 그림 위젯 프레임(BASIC · BLOSSOM · KRAFT · POLAROID · NIGHT, 꽃신 평생).
        var widgetFrame: String?
        var updatedAtMs: Int64
    }

    static var snapshotURL: URL { container.appendingPathComponent("widget/snapshot.json") }

    static func readSnapshot() -> WidgetSnapshot? {
        guard let data = try? Data(contentsOf: snapshotURL) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    /// NSE 가 작은 변경(보고싶어 횟수 · 상대 상태)을 스냅샷에 바로 반영한다.
    static func updateSnapshot(_ change: (inout WidgetSnapshot) -> Void) {
        guard var s = readSnapshot() else { return }
        change(&s)
        s.updatedAtMs = Int64(Date().timeIntervalSince1970 * 1000)
        if let data = try? JSONEncoder().encode(s) { writeProtected(data, to: snapshotURL) }
    }

    // MARK: 받은 Op 대기열 — 앱이 실행되면 :core:widget-bridge 가 DB 에 합치고 지운다

    static func enqueueReceived(opId: String, json: Data) {
        writeProtected(json, to: container.appendingPathComponent("inbox-queue/\(opId).json"))
    }

    static func writeProtected(_ data: Data, to url: URL) {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        var u = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? u.setResourceValues(values)
    }

    // MARK: Keychain

    static func secret(_ account: String) -> [UInt8]? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: keychainGroup,
            kSecReturnData as String: true,
        ]
        var out: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return [UInt8](data)
    }

    /// 기기 키 · 커플 키는 이 기기에만(ThisDeviceOnly) 둔다.
    static func setSecret(_ account: String, _ bytes: [UInt8]) {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: keychainGroup,
        ]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = Data(bytes)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
}
