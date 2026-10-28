import XCTest

/// Drives the demo node, which is what App Review uses: chatting, then reporting and blocking from a message and
/// from a node's page. One test, so the app launches once: every launch costs several seconds on the CI simulator.
final class DemoTests: XCTestCase {
    private let app = XCUIApplication()

    private func composer() -> XCUIElement { app.textViews.firstMatch.exists ? app.textViews.firstMatch : app.textFields.firstMatch }

    /// waitForExistence polls once a second even when the element is already there; most are.
    private func shows(_ e: XCUIElement, _ timeout: TimeInterval = 5) -> Bool { e.exists || e.waitForExistence(timeout: timeout) }

    private func waitGone(_ e: XCUIElement) {
        guard e.exists else { return }
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: e)
        waitForExpectations(timeout: 10)
    }

    func testDemoChatReportAndBlock() {
        continueAfterFailure = false
        app.launchArguments = ["-uitest"]
        app.launch()

        // The community terms come first.
        XCTAssert(app.staticTexts["Be kind on the mesh"].exists)
        XCTAssertFalse(app.buttons["No radio yet? Try the demo"].exists)
        app.buttons["Agree"].tap()
        app.buttons["No radio yet? Try the demo"].tap()
        XCTAssert(shows(app.staticTexts["Hiking"], 15))

        // Blocking and unblocking from a node's page, then messaging them from it.
        app.tab("Nodes")
        app.staticTexts["Leo"].tap()
        app.buttons["Block"].tap()
        XCTAssert(shows(app.buttons["Unblock"]))
        app.buttons["Unblock"].tap()
        XCTAssert(shows(app.buttons["Block"]))
        app.buttons["Message"].tap()
        XCTAssert(shows(app.textFields["Message Leo"])) // their DM
        app.backToList()
        app.tab("Chats")

        app.staticTexts["Hiking"].tap()
        let maya = app.staticTexts["Made it to the second lake. Zero cell signal here, the mesh still works 🙂"]
        XCTAssert(shows(maya, 10))

        let field = composer()
        field.tap()
        field.typeText("On my way")
        app.buttons["Send"].tap()
        XCTAssert(shows(app.staticTexts["On my way"]))
        XCTAssert(shows(app.staticTexts["Got it 👍"], 10)) // the demo node answers

        app.hideKeyboard(above: "Got it 👍") // on short screens (iPhone Duo's cover display) it covers Maya's message
        maya.press(forDuration: 1.2)
        XCTAssert(shows(app.buttons["Report"]))
        app.buttons["Report"].tap()
        XCTAssert(shows(app.buttons["Report only"]))
        app.buttons["Report only"].tap()
        // The simulator has no Mail app: the report falls back to showing the support address.
        let noMail = app.alerts["No email app"]
        if shows(noMail) { noMail.buttons["OK"].tap() }

        maya.press(forDuration: 1.2)
        XCTAssert(shows(app.buttons["Block Maya's Handheld"]))
        app.buttons["Block Maya's Handheld"].tap()
        waitGone(maya)
        XCTAssert(app.staticTexts["Copy. Dinner at 7 at camp."].exists) // others stay

        app.backToList()
        app.buttons["Settings"].tap()
        app.staticTexts["Blocked nodes"].tap()
        XCTAssert(shows(app.staticTexts["Maya's Handheld"]))
        app.buttons["Unblock"].tap()
        XCTAssert(shows(app.staticTexts["You haven't blocked anyone. To block someone, press and hold one of their messages, or open them in Nodes."]))
    }
}
