import XCTest
@testable import MeshPlus

/// Same checks as the Android app's RatingTest.
final class RatingPromptTests: XCTestCase {
    let day = RatingPrompt.day
    lazy var now = 1000 * day

    func due(first: Double? = nil, days: Int = 3, sent: Int = 20, asks: Int = 0, lastAsk: Double = 0, lastVersion: Int = 0, version: Int = 9) -> Bool {
        RatingPrompt.due(now: now, first: first ?? now - 3 * day, days: days, sent: sent, asks: asks, lastAsk: lastAsk, lastVersion: lastVersion, version: version)
    }

    func testAsksOnlyWhenEarnedAndAtMostTwice() {
        XCTAssertTrue(due())
        XCTAssertFalse(due(sent: 19))
        XCTAssertFalse(due(days: 2))
        XCTAssertFalse(due(first: now - 3 * day + 1)) // 3 days of use, but first use under 3 days ago
        XCTAssertTrue(due(asks: 1, lastAsk: now - 120 * day, lastVersion: 8))
        XCTAssertFalse(due(asks: 1, lastAsk: now - 119 * day, lastVersion: 8)) // too soon
        XCTAssertFalse(due(asks: 1, lastAsk: now - 200 * day, lastVersion: 9)) // same version
        XCTAssertFalse(due(asks: 2, lastAsk: now - 400 * day, lastVersion: 1)) // never a third time
    }
}
