import Foundation

/// Kotlin SnapshotStore와 같은 고정 inode. 잠금 파일 자체는 교체하거나 지우지 않는다.
enum SnapshotFileLock {
    enum Failure: Error { case unavailable }

    static func withLock<T>(snapshot: URL, _ work: () throws -> T) throws -> T {
        let directory = snapshot.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let fd = open(directory.appendingPathComponent(".snapshot.lock").path, O_RDWR | O_CREAT | O_CLOEXEC, 0o600)
        guard fd >= 0 else { throw Failure.unavailable }
        defer { close(fd) }
        while flock(fd, LOCK_EX) != 0 {
            guard errno == EINTR else { throw Failure.unavailable }
        }
        defer { flock(fd, LOCK_UN) }
        return try work()
    }
}
