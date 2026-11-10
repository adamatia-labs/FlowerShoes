import Foundation
import XCTest

final class RelationshipKeysTests: XCTestCase {
    func testCanonicalSnapshotWinsOverAnInterruptedMirrorAndDoesNotMixReads() throws {
        let device = Data(repeating: 1, count: 96).base64EncodedString()
        let sign = Data(repeating: 1, count: 64).base64EncodedString()
        let current = try JSONSerialization.data(withJSONObject: [
            "formatVersion": 2, "deviceSecret": device, "repairMirrors": true,
            "sharedRelationship": ["scope": "new", "values": ["device.signSecret": sign, "coupleKey.1": "AQID"]],
        ])
        var reads: [String] = []
        let reader: (String) -> [UInt8]? = { account in
            reads.append(account)
            return account == "kkotsin.vault" ? [UInt8](current) : [9, 9, 9]
        }
        let keys = try XCTUnwrap(SharedStore.readSecrets(scope: "new", read: reader))
        XCTAssertEqual(keys.secret("coupleKey.1"), [1, 2, 3])
        XCTAssertEqual(reads, ["kkotsin.vault"])
        XCTAssertNil(SharedStore.readSecrets(scope: "old", read: reader))
    }

    func testDeletedOrCorruptCanonicalStateCannotFallBackToOldMirrors() {
        for value in [#"{"formatVersion":2,"deviceSecret":null}"#, "broken"] {
            var reads: [String] = []
            let result = SharedStore.readSecrets(scope: "old") { account in
                reads.append(account)
                return account == "kkotsin.vault" ? Array(value.utf8) : [1, 2, 3]
            }
            XCTAssertNil(result)
            XCTAssertEqual(reads, ["kkotsin.vault"])
        }
    }

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
