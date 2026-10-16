import XCTest
@testable import MeshPlus

/// Same checks as the Android app's ProtoTest, plus the edge cases a Swift parser must not crash on.
final class ProtoTests: XCTestCase {
    func testRoundTrip() throws {
        let data = Pb().uint(1, 1).str(2, "привет")
        let pkt = try XCTUnwrap(Msg(Pb().fixed32(1, 0xDEAD_BEEF).fixed32(2, 0xFFFF_FFFF).msg(4, data).uint(9, 300).build))
        XCTAssertEqual(pkt.long(1), 0xDEAD_BEEF)
        XCTAssertEqual(pkt.long(2), 0xFFFF_FFFF)
        XCTAssertEqual(pkt.long(9), 300)
        XCTAssertEqual(pkt.msg(4)?.long(1), 1)
        XCTAssertEqual(pkt.msg(4)?.str(2), "привет")
        // Known bytes: ToRadio { want_config_id = 150 } and an empty Heartbeat.
        XCTAssertEqual(Pb().uint(3, 150).build, [0x18, 0x96, 0x01])
        XCTAssertEqual(Pb().msg(7, Pb()).build, [0x3A, 0x00])
        // saveRegion relies on an appended field overriding the original (protobuf last-one-wins).
        let lora = Pb().uint(1, 1).uint(7, 0).uint(8, 3).build
        let patched = try XCTUnwrap(Msg(lora + Pb().uint(7, 3).build))
        XCTAssertEqual(patched.long(7), 3)
        XCTAssertEqual(patched.long(8), 3)
        // Negative SNR travels as float bits in a fixed32.
        XCTAssertEqual(Msg(Pb().fixed32(8, Int64(Float(-4.25).bitPattern)).build)?.float(8), -4.25)
    }

    func testParsesOfficialInvite() {
        // Default LongFast invite as shared by the official apps: one channel with the well-known key 0x01.
        let rooms = inviteRooms("https://meshtastic.org/e/#CgMSAQESBggBQANIAQ")
        XCTAssertEqual(rooms.count, 1)
        XCTAssertEqual(Msg(rooms[0])?.bytes(2), [1])
        XCTAssertEqual(inviteRooms("https://example.com/no-fragment").count, 0)
        XCTAssertEqual(inviteRooms("https://meshtastic.org/e/#!!!").count, 0)
    }

    func testInviteRoundTrip() {
        let settings = Pb().bytes(2, Array(repeating: 7, count: 32)).str(3, "Hiking").build
        let link = "https://meshtastic.org/e/#" + base64url(Pb().bytes(1, settings).bytes(2, Pb().uint(7, 3).build).build)
        XCTAssertFalse(link.contains("="))
        XCTAssertEqual(inviteRooms(link), [settings])
    }

    func testMalformedInputIsRejected() {
        XCTAssertNil(Msg([0x0A, 0x05, 0x01])) // length runs past the end
        XCTAssertNil(Msg([0x80])) // unterminated varint
        XCTAssertNil(Msg([0x0B])) // wire type 3 (groups) isn't used by Meshtastic
        XCTAssertNotNil(Msg([]))
    }

    func testHelpers() {
        XCTAssertTrue(isNewer("2.8.1", "2.7.26.54e0d8d"))
        XCTAssertFalse(isNewer("2.7.26", "2.7.26.54e0d8d"))
        XCTAssertEqual(initials("Maya Handheld"), "MH")
        XCTAssertEqual("Привет мир".fitBytes(5), "Пр")
        XCTAssertEqual(suggestedRegion("DE"), 3)
        XCTAssertEqual(hexId(0x0de3_0001), "!0de30001")
    }
}
