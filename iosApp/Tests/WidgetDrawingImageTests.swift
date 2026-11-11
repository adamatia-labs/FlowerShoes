import XCTest
import UIKit

final class WidgetDrawingImageTests: XCTestCase {
    func testWideDrawingFitsSmallWidgetWithoutChangingOriginal() throws {
        try assertThumbnail(sourceSize: CGSize(width: 1456, height: 680), displayWidth: 155, scale: 3)
    }

    func testSquareDrawingFitsSmallWidgetWithoutChangingOriginal() throws {
        try assertThumbnail(sourceSize: CGSize(width: 680, height: 680), displayWidth: 155, scale: 2)
    }

    func testWideDrawingFitsMediumWidgetWithoutChangingOriginal() throws {
        try assertThumbnail(sourceSize: CGSize(width: 1456, height: 680), displayWidth: 329, scale: 3)
    }

    func testMissingAndCorruptImagesHaveNoThumbnail() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
        defer { try? FileManager.default.removeItem(at: url) }
        XCTAssertNil(WidgetDrawingImage.load(at: url, displayWidth: 155, scale: 3))
        try Data("not an image".utf8).write(to: url)
        XCTAssertNil(WidgetDrawingImage.load(at: url, displayWidth: 155, scale: 3))
    }

    func testInvalidDisplayDimensionsHaveNoThumbnail() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
        defer { try? FileManager.default.removeItem(at: url) }
        try drawing(size: CGSize(width: 680, height: 680)).write(to: url)
        for width: CGFloat in [0, -1, .infinity, .nan, .greatestFiniteMagnitude] {
            XCTAssertNil(WidgetDrawingImage.load(at: url, displayWidth: width, scale: 3))
        }
        for scale: CGFloat in [0, -1, .infinity, .nan] {
            XCTAssertNil(WidgetDrawingImage.load(at: url, displayWidth: 155, scale: scale))
        }
    }

    private func assertThumbnail(sourceSize: CGSize, displayWidth: CGFloat, scale: CGFloat) throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
        defer { try? FileManager.default.removeItem(at: url) }
        let original = drawing(size: sourceSize)
        try original.write(to: url)
        let image = try XCTUnwrap(WidgetDrawingImage.load(at: url, displayWidth: displayWidth, scale: scale)?.cgImage)
        let limit = Int(ceil(displayWidth * scale))
        XCTAssertEqual(image.width, limit)
        XCTAssertLessThanOrEqual(image.height, limit)
        XCTAssertEqual(CGFloat(image.height), CGFloat(image.width) * sourceSize.height / sourceSize.width, accuracy: 1)
        XCTAssertTrue([CGImageAlphaInfo.first, .last, .premultipliedFirst, .premultipliedLast].contains(image.alphaInfo))
        XCTAssertEqual(try Data(contentsOf: url), original)
    }

    private func drawing(size: CGSize) -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format).pngData { context in
            UIColor.red.setFill()
            context.fill(CGRect(origin: .zero, size: CGSize(width: size.width / 2, height: size.height)))
        }
    }
}
