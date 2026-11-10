import Foundation

/// The sequence and its encrypted envelope are one durable record. A crash can repeat an
/// envelope, but cannot consume a sequence without leaving the exact envelope to recover.
final class WidgetSendJournal {
    struct Envelope: Codable, Equatable {
        let opId: String
        let body: Data
    }

    private struct State: Codable {
        var lastSeq: Int64
        var pending: Envelope?
    }

    enum Checkpoint: Equatable { case journalSaved, outboxSaved }
    enum Failure: Error { case lock, invalidCounter, invalidEnvelope, exhausted }

    private let root: URL
    private let checkpoint: (Checkpoint) throws -> Void
    private var journalURL: URL { root.appendingPathComponent("widget-send-journal.json") }

    init(root: URL, checkpoint: @escaping (Checkpoint) throws -> Void = { _ in }) {
        self.root = root
        self.checkpoint = checkpoint
    }

    func enqueue(_ build: (Int64) throws -> Envelope) throws -> Envelope {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        // A stable inode: never atomically replace the file that carries the process lock.
        // Use the old counter's lock as well so already-running old extensions serialize here.
        let counter = root.appendingPathComponent("widget-seq")
        let fd = open(counter.path, O_RDWR | O_CREAT, 0o600)
        guard fd >= 0 else { throw Failure.lock }
        defer { close(fd) }
        guard flock(fd, LOCK_EX) == 0 else { throw Failure.lock }
        defer { flock(fd, LOCK_UN) }

        let oldCounter = try legacyCounter(counter)
        var state: State
        if FileManager.default.fileExists(atPath: journalURL.path) {
            state = try JSONDecoder().decode(State.self, from: Data(contentsOf: journalURL))
            guard state.lastSeq >= 0 else { throw Failure.invalidCounter }
        } else {
            state = State(lastSeq: oldCounter, pending: nil)
        }
        if let pending = state.pending {
            try publish(pending)
            state.pending = nil
            try persist(state)
        }
        state.lastSeq = max(state.lastSeq, oldCounter)
        guard state.lastSeq < Int64.max else { throw Failure.exhausted }
        let next = state.lastSeq + 1
        let envelope = try build(next)
        try validate(envelope)
        // All fallible construction precedes sequence allocation. A failed write does not
        // permit sending; if an atomic write committed before an error, the next call recovers it.
        state = State(lastSeq: next, pending: envelope)
        try persist(state)
        try checkpoint(.journalSaved)
        try publish(envelope)
        try checkpoint(.outboxSaved)
        // Old extensions still read the legacy counter. Only advance it after an envelope exists.
        let text = Array(String(next).utf8)
        guard ftruncate(fd, 0) == 0, lseek(fd, 0, SEEK_SET) >= 0,
              text.withUnsafeBufferPointer({ write(fd, $0.baseAddress, text.count) }) == text.count,
              fsync(fd) == 0 else { throw Failure.invalidCounter }
        state.pending = nil
        try persist(state)
        return envelope
    }

    private func legacyCounter(_ url: URL) throws -> Int64 {
        let data = try Data(contentsOf: url)
        if data.isEmpty { return 0 }
        guard let raw = String(data: data, encoding: .utf8),
              let value = Int64(raw.trimmingCharacters(in: .whitespacesAndNewlines)), value >= 0 else {
            throw Failure.invalidCounter
        }
        return value
    }

    private func validate(_ envelope: Envelope) throws {
        guard UUID(uuidString: envelope.opId) != nil, !envelope.body.isEmpty else { throw Failure.invalidEnvelope }
    }

    private func publish(_ envelope: Envelope) throws {
        try validate(envelope)
        let file = root.appendingPathComponent("widget-outbox/\(envelope.opId).json")
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try writeProtected(envelope.body, to: file)
    }

    private func persist(_ state: State) throws {
        try writeProtected(JSONEncoder().encode(state), to: journalURL)
    }

    private func writeProtected(_ data: Data, to file: URL) throws {
        try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        var url = file
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try url.setResourceValues(values)
    }
}
