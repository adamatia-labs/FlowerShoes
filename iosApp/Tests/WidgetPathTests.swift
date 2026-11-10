import Foundation
import XCTest

final class WidgetPathTests: XCTestCase {
    func testWidgetImageMustBeDirectChildOfWidgetDirectory() {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("kk-widget-path-tests", isDirectory: true)
        let good = dir.appendingPathComponent("drawing-safe.png")
        XCTAssertNotNil(SharedStore.safeWidgetImageURL(good.path, directory: dir))
        XCTAssertNil(SharedStore.safeWidgetImageURL(dir.appendingPathComponent("../drawing-outside.png").path, directory: dir))
        XCTAssertNil(SharedStore.safeWidgetImageURL(dir.appendingPathComponent("nested/drawing-image.png").path, directory: dir))
        XCTAssertNil(SharedStore.safeWidgetImageURL(dir.appendingPathComponent("other.png").path, directory: dir))
        XCTAssertNil(SharedStore.safeWidgetImageURL(nil, directory: dir))
        XCTAssertTrue(SharedStore.isSafeWidgetDrawingID("normal-id"))
        XCTAssertFalse(SharedStore.isSafeWidgetDrawingID("../outside"))
        XCTAssertFalse(SharedStore.isSafeWidgetDrawingID(nil))
    }

    func testWidgetImageCannotFollowSymlinkOutsideWidgetDirectory() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let dir = root.appendingPathComponent("widget", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let outside = root.appendingPathComponent("private.png")
        try Data([1]).write(to: outside)
        let link = dir.appendingPathComponent("drawing-link.png")
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: outside)
        XCTAssertNil(SharedStore.safeWidgetImageURL(link.path, directory: dir))
    }
}
