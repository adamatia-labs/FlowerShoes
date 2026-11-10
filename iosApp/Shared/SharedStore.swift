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
    static var keychainGroup: String? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "KkotsinKeychainAccessGroup") as? String,
              group.hasSuffix(".com.sadturtleman.kkotsin.shared") else { return nil }
        return group
    }

    static var container: URL {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)!
    }

    // MARK: 위젯 스냅샷 — :core:widget-bridge WidgetSnapshot 과 같은 필드

    struct WidgetTag: Codable {
        var label: String
        var value: String
    }

    /// 글자는 Kotlin(widgetSnapshot)이 만들어 둔다 — 위젯은 그대로 그린다.
    struct WidgetSnapshot: Codable {
        var version: Int
        var theme: String
        var ddayCaption: String?
        var ddayValue: String?
        var ddayFoot: String?
        var progress: Double?
        var progressStart: String?
        var progressPercent: String?
        var progressEnd: String?
        var tagLeave: WidgetTag?
        var tagMeet: WidgetTag?
        var tagRank: WidgetTag?
        var partnerName: String?
        var partnerStatusCaption: String?
        var partnerStatus: String?
        /// 상대가 고른 상태 색(ARGB).
        var partnerStatusColor: Int64?
        var partnerStatusUntil: String?
        var nextEventDays: Int?
        var nextEventValue: String?
        var nextEventType: String?
        var nextEventLine: String?
        var nextEventWhen: String?
        // v2 에서 생긴 수는 옛 스냅샷에 없을 수 있어 선택값이다(앱이 다시 쓰기 전에도 위젯 · NSE 가 읽히게).
        var unreadLetters: Int?
        var letterLine: String?
        var letterMeta: String?
        var letterId: String?
        var missYouFromPartnerToday: Int
        var missYouSentToday: Int?
        /// 방금(1분 안) 보낸 보고싶어 시각 — 위젯이 "보냈어요" 상태로 그린다.
        var missYouJustSentAtMs: Int64?
        var linked: Bool
        var drawingDocPath: String?
        var drawingPngPath: String?
        var drawingShape: String?
        var drawingLetterId: String?
        var drawingId: String?
        var drawingCaption: String?
        var drawingMeta: String?
        /// 받은 그림 위젯 프레임(BASIC · BLOSSOM · KRAFT · POLAROID · NIGHT, 꽃신 평생).
        var widgetFrame: String?
        var updatedAtMs: Int64
    }

    static var snapshotURL: URL { container.appendingPathComponent("widget/snapshot.json") }

    /// 예전 스냅샷도 App Group의 위젯 그림 폴더 밖은 읽지 않는다.
    static func safeWidgetImageURL(_ path: String?, directory: URL) -> URL? {
        guard let path else { return nil }
        let root = directory.standardizedFileURL.resolvingSymlinksInPath()
        let file = URL(fileURLWithPath: path).standardizedFileURL.resolvingSymlinksInPath()
        guard file.deletingLastPathComponent().path == root.path,
              file.lastPathComponent.hasPrefix("drawing-"), file.pathExtension == "png" else { return nil }
        return file
    }

    static func isSafeWidgetDrawingID(_ id: String?) -> Bool {
        guard let id, !id.isEmpty, id != ".", id != "..", id.utf8.count <= 255 else { return false }
        return !id.unicodeScalars.contains { scalar in
            scalar == "/" || scalar == "\\" || scalar == ":" || scalar.value < 32 || scalar.value == 127
        }
    }

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

    struct RelationshipKeys: Decodable {
        let scope: String
        let values: [String: String]

        static func decode(_ data: Data, expectedScope: String) -> RelationshipKeys? {
            guard let keys = try? JSONDecoder().decode(Self.self, from: data), keys.scope == expectedScope else { return nil }
            return keys
        }

        func secret(_ account: String) -> [UInt8]? {
            guard let value = values[account], let data = Data(base64Encoded: value) else { return nil }
            return [UInt8](data)
        }
    }

    private struct VaultState: Decodable {
        let formatVersion: Int?
        let deviceSecret: String?
        let coupleKeys: [String: String]?
        let partnerSignPub: String?
        let sharedRelationship: RelationshipKeys?
    }

    /// 한 번 읽은 원본으로 서명 키와 관계 키를 함께 고른다. 미러 복구가 중단돼도 이전 키를 섞지 않는다.
    static func readSecrets(scope: String?) -> RelationshipKeys? {
        readSecrets(scope: scope, read: { secret($0) })
    }

    static func readSecrets(scope: String?, read: (String) -> [UInt8]?) -> RelationshipKeys? {
        guard let bytes = read("kkotsin.vault"),
              let state = try? JSONDecoder().decode(VaultState.self, from: Data(bytes)),
              let encodedDevice = state.deviceSecret,
              let device = Data(base64Encoded: encodedDevice), device.count == 96 else { return nil }
        let version = state.formatVersion ?? 1
        guard version == 1 || version == 2 else { return nil }
        if let scope {
            let shared: RelationshipKeys?
            if version == 2 {
                shared = state.sharedRelationship
            } else {
                shared = read("relationship.keys").flatMap { RelationshipKeys.decode(Data($0), expectedScope: scope) }
                guard read("kkotsin.vault") == bytes else { return nil }
            }
            guard let shared, shared.scope == scope,
                  shared.secret("device.signSecret") == Array(device.prefix(64)) else { return nil }
            return shared
        }
        let partner: String?
        if version == 2 {
            partner = state.partnerSignPub
        } else {
            partner = read("partner.signPub").map { Data($0).base64EncodedString() }
            guard read("kkotsin.vault") == bytes else { return nil }
        }
        guard let partner, Data(base64Encoded: partner)?.count == 32 else { return nil }
        var values = Dictionary(uniqueKeysWithValues: (state.coupleKeys ?? [:]).map { ("coupleKey.\($0.key)", $0.value) })
        values["device.signSecret"] = Data(device.prefix(64)).base64EncodedString()
        values["partner.signPub"] = partner
        return RelationshipKeys(scope: "legacy", values: values)
    }

    private static func secret(_ account: String) -> [UInt8]? {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: "",
            kSecReturnData as String: true,
        ]
        if let keychainGroup { query[kSecAttrAccessGroup as String] = keychainGroup }
        var out: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return [UInt8](data)
    }

    /// 기기 키 · 커플 키는 이 기기에만(ThisDeviceOnly) 둔다.
    static func setSecret(_ account: String, _ bytes: [UInt8]) {
        var base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: "",
        ]
        if let keychainGroup { base[kSecAttrAccessGroup as String] = keychainGroup }
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = Data(bytes)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
}
