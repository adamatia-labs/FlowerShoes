import XCTest

final class WidgetSendJournalTests: XCTestCase {
    private var root: URL!
    private enum Crash: Error { case injected }

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws { try FileManager.default.removeItem(at: root) }

    private func envelope(_ seq: Int64) -> WidgetSendJournal.Envelope {
        WidgetSendJournal.Envelope(opId: UUID().uuidString, body: Data("encrypted-\(seq)".utf8))
    }

    func testConstructionFailureDoesNotConsumeSequence() throws {
        let journal = WidgetSendJournal(root: root)
        XCTAssertThrowsError(try journal.enqueue { _ in throw Crash.injected })
        var received: Int64 = 0
        _ = try journal.enqueue { received = $0; return self.envelope($0) }
        XCTAssertEqual(received, 1)
    }

    func testCrashAfterJournalRecoversExactEnvelopeBeforeNextAllocation() throws {
        var original: WidgetSendJournal.Envelope!
        let beforeCrash = WidgetSendJournal(root: root) { checkpoint in
            if checkpoint == .journalSaved { throw Crash.injected }
        }
        XCTAssertThrowsError(try beforeCrash.enqueue { original = self.envelope($0); return original })
        let file = root.appendingPathComponent("widget-outbox/\(original.opId).json")
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
        var next: Int64 = 0
        _ = try WidgetSendJournal(root: root).enqueue { next = $0; return self.envelope($0) }
        XCTAssertEqual(next, 2)
        XCTAssertEqual(try Data(contentsOf: file), original.body)
    }

    func testAppImportBetweenPublicationAndCrashDoesNotReuseSequence() throws {
        var original: WidgetSendJournal.Envelope!
        let beforeCrash = WidgetSendJournal(root: root) { checkpoint in
            if checkpoint == .outboxSaved {
                // The app committed and deleted the file before the extension was killed.
                try FileManager.default.removeItem(at: self.root.appendingPathComponent("widget-outbox/\(original.opId).json"))
                throw Crash.injected
            }
        }
        XCTAssertThrowsError(try beforeCrash.enqueue { original = self.envelope($0); return original })
        var next: Int64 = 0
        _ = try WidgetSendJournal(root: root).enqueue { next = $0; return self.envelope($0) }
        XCTAssertEqual(next, 2)
        XCTAssertEqual(try Data(contentsOf: root.appendingPathComponent("widget-outbox/\(original.opId).json")), original.body)
    }

    func testIndependentInstancesSerializeWithoutGaps() throws {
        let lock = NSLock()
        var sequences: [Int64] = []
        var errors: [Error] = []
        DispatchQueue.concurrentPerform(iterations: 20) { _ in
            do {
                _ = try WidgetSendJournal(root: root).enqueue { seq in
                    lock.lock(); sequences.append(seq); lock.unlock()
                    return self.envelope(seq)
                }
            } catch { lock.lock(); errors.append(error); lock.unlock() }
        }
        XCTAssertTrue(errors.isEmpty)
        XCTAssertEqual(sequences.sorted(), Array(1...20).map(Int64.init))
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.appendingPathComponent("widget-outbox").path).count, 20)
    }

    func testLegacyCounterIsNotReusedAndCorruptionFailsClosed() throws {
        let counter = root.appendingPathComponent("widget-seq")
        try Data("9".utf8).write(to: counter)
        var next: Int64 = 0
        _ = try WidgetSendJournal(root: root).enqueue { next = $0; return self.envelope($0) }
        XCTAssertEqual(next, 10)
        try Data("corrupt".utf8).write(to: counter)
        XCTAssertThrowsError(try WidgetSendJournal(root: root).enqueue { self.envelope($0) })
    }

    private func outgoing(_ seq: Int64, couple: String = "couple") throws -> WidgetSendJournal.Envelope {
        let id = UUID().uuidString
        let header = OpEnvelope.Header(opId: id, stream: "device:widget", seq: seq, keyEpoch: 1)
        let body = try JSONSerialization.data(withJSONObject: [
            "coupleId": couple, "fromDeviceId": "device", "toDeviceId": "partner", "opId": id,
            "header": Data(header.bytes()).base64EncodedString(), "ct": "encrypted", "sig": "signature",
        ])
        return WidgetSendJournal.Envelope(opId: id, body: body)
    }

    func testNextWidgetTapDeliversRecoveredEnvelopeFirstWithoutOpeningApp() async throws {
        let beforeCrash = WidgetSendJournal(root: root) { if $0 == .journalSaved { throw Crash.injected } }
        var first: WidgetSendJournal.Envelope!
        XCTAssertThrowsError(try beforeCrash.enqueue { first = try self.outgoing($0); return first })
        let second = try WidgetSendJournal(root: root).enqueue { try self.outgoing($0) }
        // A previous relationship's copy must remain private and must not block this delivery.
        _ = try WidgetSendJournal(root: root).enqueue { try self.outgoing($0, couple: "previous") }
        let destination = WidgetOutboxDelivery.Destination(coupleId: "couple", deviceId: "device", partnerDeviceId: "partner")
        var delivered: [Data] = []
        try await WidgetOutboxDelivery.send(root: root, destination: destination) { delivered.append($0) }
        XCTAssertEqual(delivered, [first.body, second.body])
        XCTAssertTrue(FileManager.default.fileExists(atPath: root.appendingPathComponent("widget-outbox/\(first.opId).json").path))
    }

    func testTransportFailureRetainsOriginalAndRetriesInOrder() async throws {
        let first = try WidgetSendJournal(root: root).enqueue { try self.outgoing($0) }
        let second = try WidgetSendJournal(root: root).enqueue { try self.outgoing($0) }
        let destination = WidgetOutboxDelivery.Destination(coupleId: "couple", deviceId: "device", partnerDeviceId: "partner")
        do {
            try await WidgetOutboxDelivery.send(root: root, destination: destination) { _ in throw Crash.injected }
            XCTFail("transport failure must propagate")
        } catch Crash.injected { }
        var delivered: [Data] = []
        try await WidgetOutboxDelivery.send(root: root, destination: destination) { delivered.append($0) }
        XCTAssertEqual(delivered, [first.body, second.body])
    }
}
