import XCTest

/// Folding an iPhone Duo flips the width size class while the app runs. The app, launched with `-foldtest`, flips it on
/// every `FoldTest` Darwin notification; this checks the open conversation, its draft, the keyboard and a sheet all
/// come through a fold and an unfold. Runs on any simulator: compact phones fold "open", iPads (in a
/// `TARGETED_DEVICE_FAMILY=1,2` build) fold "shut".
final class FoldTests: XCTestCase {
    private let app = XCUIApplication()

    /// Only the regular layout has the sidebar's Chats/Nodes switch.
    private var regular: Bool { app.segmentedControls.firstMatch.exists }

    /// `covered`: a sheet is up, hiding the layout underneath from the test.
    private func fold(covered: Bool = false, file: StaticString = #filePath, line: UInt = #line) {
        let wasRegular = covered ? false : regular
        CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(),
                                             CFNotificationName("com.backmeupplz.meshplus.fold" as CFString), nil, nil, true)
        sleep(1) // let the other layout come in
        guard !covered else { return }
        let switched = wasRegular ? app.segmentedControls.firstMatch.waitForNonExistence(timeout: 5)
                                  : app.segmentedControls.firstMatch.waitForExistence(timeout: 5)
        XCTAssert(switched, "the layout didn't switch", file: file, line: line)
    }

    private func composer() -> XCUIElement { app.textViews.firstMatch.exists ? app.textViews.firstMatch : app.textFields.firstMatch }

    private func assertConversation(_ draft: String, keyboard: Bool, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssert(app.staticTexts["Copy. Dinner at 7 at camp."].waitForExistence(timeout: 5), "conversation closed", file: file, line: line)
        XCTAssertEqual(composer().value as? String, draft, "draft lost", file: file, line: line)
        // Focus rather than app.keyboards: with a hardware keyboard attached (iPad simulators) no software one shows.
        if keyboard {
            let focused = NSPredicate { e, _ in ((e as? XCUIElement)?.value(forKey: "hasKeyboardFocus") as? Bool) == true }
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: focused, object: composer())], timeout: 10), .completed,
                           "keyboard dropped", file: file, line: line)
        }
    }

    func testStateSurvivesFolding() {
        continueAfterFailure = false
        app.launchArguments = ["-uitest", "-foldtest"]
        app.launch()
        app.buttons["Agree"].tap()
        app.buttons["No radio yet? Try the demo"].tap()
        XCTAssert(app.staticTexts["Hiking"].waitForExistence(timeout: 15))

        // With nothing open, folding either way opens nothing (an open conversation is marked read).
        fold()
        XCTAssertFalse(app.staticTexts["Copy. Dinner at 7 at camp."].exists, "a conversation opened by itself")
        fold()
        XCTAssertFalse(app.staticTexts["Copy. Dinner at 7 at camp."].exists, "a conversation opened by itself")

        app.staticTexts["Hiking"].tap()
        XCTAssert(app.staticTexts["Copy. Dinner at 7 at camp."].waitForExistence(timeout: 10))
        let field = composer()
        field.tap()
        field.typeText("Half-typed")
        assertConversation("Half-typed", keyboard: true)

        fold()
        assertConversation("Half-typed", keyboard: true)
        fold()
        assertConversation("Half-typed", keyboard: true)

        // A sheet stays up through a fold, and the draft is still there under it.
        app.buttons["Invite"].tap()
        XCTAssert(app.staticTexts["Invite to Hiking"].waitForExistence(timeout: 5))
        fold(covered: true)
        XCTAssert(app.staticTexts["Invite to Hiking"].waitForExistence(timeout: 5), "sheet dismissed by folding")
        fold(covered: true)
        XCTAssert(app.staticTexts["Invite to Hiking"].exists, "sheet dismissed by unfolding")
        app.swipeDown(velocity: .fast)
        XCTAssert(app.staticTexts["Copy. Dinner at 7 at camp."].waitForExistence(timeout: 5))
        XCTAssertEqual(composer().value as? String, "Half-typed")

        // Sending clears the draft in both layouts.
        app.buttons["Send"].tap()
        XCTAssert(app.staticTexts["Half-typed"].waitForExistence(timeout: 5))
        fold()
        XCTAssert(app.staticTexts["Half-typed"].waitForExistence(timeout: 5))
        XCTAssertNotEqual(composer().value as? String, "Half-typed")
    }
}
