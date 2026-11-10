import XCTest

final class BoundedZstdTests: XCTestCase {
    func testNormalRoundTripAndExactLimit() throws {
        let raw = Data(String(repeating: "그림 문서 ", count: 300).utf8)
        let frame = try XCTUnwrap(BoundedZstd.compress(raw, level: 3))
        XCTAssertEqual(BoundedZstd.decompress(frame, maxOutputBytes: raw.count), raw)
        XCTAssertNil(BoundedZstd.decompress(frame, maxOutputBytes: raw.count - 1))
    }

    func testCorruptedAndTruncatedFramesDoNotTrap() throws {
        XCTAssertNil(BoundedZstd.decompress(Data([0x28, 0xb5, 0x2f, 0xfd, 0x7f, 1, 2]), maxOutputBytes: 1024))
        let frame = try XCTUnwrap(BoundedZstd.compress(Data(repeating: 1, count: 1024), level: 3))
        XCTAssertNil(BoundedZstd.decompress(Data(frame.dropLast()), maxOutputBytes: 1024))
        XCTAssertNil(BoundedZstd.decompress(Data(), maxOutputBytes: 1024))
    }

    func testUnknownSizeAndHugeDeclaredSizeAreRejectedBeforeAllocation() {
        let unknown = Data([0x28, 0xb5, 0x2f, 0xfd, 0x00, 0x00, 1, 0, 0])
        let huge = Data([0x28, 0xb5, 0x2f, 0xfd, 0xe0] + Array(repeating: UInt8(0xff), count: 8) + [1, 0, 0])
        XCTAssertNil(BoundedZstd.decompress(unknown, maxOutputBytes: BoundedZstd.maxDecodedBytes))
        XCTAssertNil(BoundedZstd.decompress(huge, maxOutputBytes: BoundedZstd.maxDecodedBytes))
        XCTAssertNil(BoundedZstd.decompress(huge, maxOutputBytes: -1))
    }

    func testOversizedExistingFrameIsPreserved() {
        // Single-segment header declares 16 MiB + 1. Its tiny body must not cause that allocation.
        let frame = Data([0x28, 0xb5, 0x2f, 0xfd, 0xa0, 1, 0, 0, 1, 1, 0, 0])
        let original = frame
        XCTAssertNil(BoundedZstd.decompress(frame, maxOutputBytes: BoundedZstd.maxDecodedBytes))
        XCTAssertEqual(frame, original)
    }
}
