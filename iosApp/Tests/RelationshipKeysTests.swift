import Foundation
import XCTest

final class RelationshipKeysTests: XCTestCase {
    func testAcceptsOnlyThePublishedRelationshipAndEpoch() throws {
        let data = Data(#"{"scope":"ac:device-a:2","values":{"coupleKey.2":"AQID","partner.signPub":"BAUG"}}"#.utf8)
        let keys = try XCTUnwrap(SharedStore.RelationshipKeys.decode(data, expectedScope: "ac:device-a:2"))
        XCTAssertEqual(keys.secret("coupleKey.2"), [1, 2, 3])
        XCTAssertNil(keys.secret("coupleKey.1"))
        XCTAssertNil(SharedStore.RelationshipKeys.decode(data, expectedScope: "ab:device-a:2"))
        XCTAssertNil(SharedStore.RelationshipKeys.decode(data, expectedScope: "ac:device-a:1"))
        XCTAssertNil(SharedStore.RelationshipKeys.decode(Data("broken".utf8), expectedScope: "ac:device-a:2"))
    }
}
