import XCTest

/// Drives the demo node, which is what App Review uses: chatting, then reporting and blocking from a message.
final class DemoTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments = ["-uitest"]
        app.launch()
    }

    private func startDemo() {
        app.buttons["Agree"].tap()
        app.buttons["No radio yet? Try the demo"].tap()
        XCTAssert(app.staticTexts["Hiking"].waitForExistence(timeout: 15))
    }

    private func composer() -> XCUIElement { app.textViews.firstMatch.exists ? app.textViews.firstMatch : app.textFields.firstMatch }

    private func waitGone(_ e: XCUIElement) {
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: e)
        waitForExpectations(timeout: 10)
    }

    func testTermsComeFirst() {
        XCTAssert(app.staticTexts["Be kind on the mesh"].exists)
        XCTAssertFalse(app.buttons["No radio yet? Try the demo"].exists)
    }

    func testChatReportBlockAndUnblock() {
        startDemo()
        app.staticTexts["Hiking"].tap()
        let maya = app.staticTexts["Made it to the second lake. Zero cell signal here, the mesh still works 🙂"]
        XCTAssert(maya.waitForExistence(timeout: 10))

        let field = composer()
        field.tap()
        field.typeText("On my way")
        app.buttons["Send"].tap()
        XCTAssert(app.staticTexts["On my way"].waitForExistence(timeout: 5))
        XCTAssert(app.staticTexts["Got it 👍"].waitForExistence(timeout: 10)) // the demo node answers

        maya.press(forDuration: 1.2)
        XCTAssert(app.buttons["Report"].waitForExistence(timeout: 5))
        app.buttons["Report"].tap()
        XCTAssert(app.buttons["Report only"].waitForExistence(timeout: 5))
        app.buttons["Report only"].tap()
        // The simulator has no Mail app: the report falls back to showing the support address.
        let noMail = app.alerts["No email app"]
        if noMail.waitForExistence(timeout: 5) { noMail.buttons["OK"].tap() }

        maya.press(forDuration: 1.2)
        XCTAssert(app.buttons["Block Maya's Handheld"].waitForExistence(timeout: 5))
        app.buttons["Block Maya's Handheld"].tap()
        waitGone(maya)
        XCTAssert(app.staticTexts["Copy. Dinner at 7 at camp."].exists) // others stay

        app.navigationBars.buttons.element(boundBy: 0).tap() // back to Chats
        app.buttons["Settings"].tap()
        app.staticTexts["Blocked nodes"].tap()
        XCTAssert(app.staticTexts["Maya's Handheld"].waitForExistence(timeout: 5))
        app.buttons["Unblock"].tap()
        XCTAssert(app.staticTexts["You haven't blocked anyone. To block someone, press and hold one of their messages, or open them in Nodes."].waitForExistence(timeout: 5))
    }

    func testBlockFromProfile() {
        startDemo()
        app.tabBars.buttons["Nodes"].tap()
        app.staticTexts["Leo"].tap()
        app.buttons["Block"].tap()
        XCTAssert(app.buttons["Unblock"].waitForExistence(timeout: 5))
    }
}
