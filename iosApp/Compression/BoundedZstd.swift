import Foundation
import libzstd

/// App bridge and Swift tests use the same non-trapping libzstd path.
enum BoundedZstd {
    static let maxDecodedBytes = 16 * 1024 * 1024

    static func compress(_ src: Data, level: Int32) -> Data? {
        guard src.count <= maxDecodedBytes else { return nil }
        var out = Data(count: ZSTD_compressBound(src.count))
        let n = out.withUnsafeMutableBytes { dst in
            src.withUnsafeBytes { s in ZSTD_compress(dst.baseAddress, dst.count, s.baseAddress, s.count, level) }
        }
        guard ZSTD_isError(n) == 0, n <= out.count else { return nil }
        return out.prefix(n)
    }

    static func decompress(_ src: Data, maxOutputBytes: Int) -> Data? {
        guard (0...maxDecodedBytes).contains(maxOutputBytes), src.count <= maxDecodedBytes else { return nil }
        let size = src.withUnsafeBytes { ZSTD_getFrameContentSize($0.baseAddress, $0.count) }
        guard size != ZSTD_CONTENTSIZE_ERROR, size != ZSTD_CONTENTSIZE_UNKNOWN,
              size <= UInt64(maxOutputBytes) else { return nil }
        // Convert only after bounding the untrusted UInt64; no precondition or huge Data allocation.
        var out = Data(count: Int(size))
        let n = out.withUnsafeMutableBytes { dst in
            src.withUnsafeBytes { s in ZSTD_decompress(dst.baseAddress, dst.count, s.baseAddress, s.count) }
        }
        guard ZSTD_isError(n) == 0, n == out.count else { return nil }
        return out
    }
}
