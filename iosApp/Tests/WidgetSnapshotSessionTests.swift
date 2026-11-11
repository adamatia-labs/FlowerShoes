import XCTest

final class WidgetSnapshotSessionTests: XCTestCase {
    private var root: URL!
    private var file: URL { root.appendingPathComponent("widget/snapshot.json") }

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws { try FileManager.default.removeItem(at: root) }

    private func owner(session: String = "login-a", couple: String = "couple-a", epoch: Int = 1) -> SharedStore.WidgetOwner {
        .init(userId: "user", deviceId: "device", accountSession: session, coupleId: couple,
              partnerUserId: "partner", keyEpoch: epoch, linkedAtMs: 100)
    }

    private func snapshot(owner: SharedStore.WidgetOwner?) throws -> Data {
        var value: [String: Any] = ["version": 2, "theme": "GOMSIN", "linked": true,
                                   "missYouFromPartnerToday": 0, "missYouSentToday": 0, "updatedAtMs": 1]
        if let owner { value["owner"] = try JSONSerialization.jsonObject(with: JSONEncoder().encode(owner)) }
        return try JSONSerialization.data(withJSONObject: value)
    }

    func testDelayedCompletionCannotChangeNewRelationshipOrLoginOrKey() throws {
        let old = owner()
        for replacement in [owner(couple: "couple-b"), owner(session: "login-b"), owner(epoch: 2)] {
            let before = try snapshot(owner: replacement)
            try before.write(to: file, options: .atomic)
            XCTAssertFalse(SharedStore.updateSnapshot(owner: old, at: file) {
                $0.missYouSentToday = 99
                $0.partnerStatus = "old status"
            })
            XCTAssertEqual(try Data(contentsOf: file), before)
        }
    }

    func testMatchingOwnerUpdatesCountsAndStatus() throws {
        let current = owner()
        try snapshot(owner: current).write(to: file, options: .atomic)
        XCTAssertTrue(SharedStore.updateSnapshot(owner: current, at: file) {
            $0.missYouSentToday = 1
            $0.missYouFromPartnerToday = 2
            $0.partnerStatus = "current status"
        })
        let saved = try XCTUnwrap(SharedStore.readSnapshot(at: file))
        XCTAssertEqual(saved.missYouSentToday, 1)
        XCTAssertEqual(saved.missYouFromPartnerToday, 2)
        XCTAssertEqual(saved.partnerStatus, "current status")
    }

    func testMissingOwnershipIsReadableButCannotAuthorizeExtensionWrite() throws {
        let before = try snapshot(owner: nil)
        try before.write(to: file, options: .atomic)
        XCTAssertNotNil(SharedStore.readSnapshot(at: file))
        XCTAssertFalse(SharedStore.updateSnapshot(owner: owner(), at: file) { $0.missYouSentToday = 99 })
        XCTAssertFalse(SharedStore.updateSnapshot(owner: nil, at: file) { $0.missYouSentToday = 99 })
        XCTAssertEqual(try Data(contentsOf: file), before)
    }

    func testAppReplacementCannotInterleaveExtensionReadAndWrite() throws {
        let old = owner(), replacement = owner(couple: "couple-b")
        let url = file
        let next = try snapshot(owner: replacement)
        try snapshot(owner: old).write(to: url, options: .atomic)
        let editing = DispatchSemaphore(value: 0), release = DispatchSemaphore(value: 0)
        let updated = DispatchSemaphore(value: 0), replacing = DispatchSemaphore(value: 0), replaced = DispatchSemaphore(value: 0)
        defer { release.signal() }
        DispatchQueue.global().async {
            XCTAssertTrue(SharedStore.updateSnapshot(owner: old, at: url) {
                editing.signal()
                XCTAssertEqual(release.wait(timeout: .now() + 5), .success)
                $0.missYouSentToday = 1
            })
            updated.signal()
        }
        XCTAssertEqual(editing.wait(timeout: .now() + 5), .success)
        DispatchQueue.global().async {
            replacing.signal()
            // Kotlin SnapshotStore uses this same stable .snapshot.lock file before its atomic move.
            do { try SnapshotFileLock.withLock(snapshot: url) { try next.write(to: url, options: .atomic) } }
            catch { XCTFail("snapshot replacement failed: \(error)") }
            replaced.signal()
        }
        XCTAssertEqual(replacing.wait(timeout: .now() + 5), .success)
        XCTAssertEqual(replaced.wait(timeout: .now() + 0.05), .timedOut)
        release.signal()
        XCTAssertEqual(updated.wait(timeout: .now() + 5), .success)
        XCTAssertEqual(replaced.wait(timeout: .now() + 5), .success)
        XCTAssertEqual(try Data(contentsOf: url), next)
    }
}
