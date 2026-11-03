import XCTest

/// App Review's 1.2 screen recording at a watchable pace, on the demo node: terms, the word filter, reporting a message,
/// blocking its sender (both tell the developer) and the Settings that undo them. Skipped unless
/// TEST_RUNNER_REVIEW_VIDEO=1, while `xcrun devicectl device capture screen-record` records the phone.
final class ReviewVideo: XCTestCase {
    private let app = XCUIApplication()
    private func pause(_ s: Double = 2.5) { Thread.sleep(forTimeInterval: s) }
    private func show(_ e: XCUIElement) { XCTAssert(e.waitForExistence(timeout: 15), "\(e)") }

    /// The confirmation that the developer was told: let it show, then close it.
    private func confirm(_ title: String) {
        show(app.alerts[title])
        pause(4)
        app.alerts[title].buttons["OK"].tap()
        pause()
    }

    func testWalkthrough() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["REVIEW_VIDEO"] == "1")
        continueAfterFailure = false
        app.launchArguments = ["-uitest", "-animations"]
        app.launch()

        show(app.staticTexts["Be kind on the mesh"])
        pause(6)
        app.buttons["Agree"].tap()
        pause()
        app.buttons["No radio yet? Try the demo"].tap()
        show(app.staticTexts["Hiking"])
        pause()

        app.staticTexts["Hiking"].tap()
        let maya = app.staticTexts["Made it to the second lake. Zero cell signal here, the mesh still works 🙂"]
        show(maya)
        pause()
        let field = app.textViews.firstMatch.exists ? app.textViews.firstMatch : app.textFields.firstMatch
        field.tap()
        field.typeText("This trail is shit, take the road")
        pause(1.5)
        app.buttons["Send"].tap()
        show(app.staticTexts["This trail is ••••, take the road"]) // the filter masks it
        pause(4)
        app.hideKeyboard(above: "Got it 👍")
        pause()

        maya.press(forDuration: 1.2)
        show(app.buttons["Report"])
        pause()
        app.buttons["Report"].tap()
        show(app.buttons["Report only"])
        pause(4)
        app.buttons["Report only"].tap()
        confirm("Report sent")

        maya.press(forDuration: 1.2)
        show(app.buttons["Block Maya's Handheld"])
        pause()
        app.buttons["Block Maya's Handheld"].tap()
        confirm("Blocked")
        XCTAssertFalse(maya.exists) // gone at once
        pause(3)

        app.backToList()
        pause()
        app.buttons["Settings"].tap()
        show(app.switches["Filter objectionable words"])
        pause(4)
        app.staticTexts["Blocked nodes"].tap()
        show(app.staticTexts["Maya's Handheld"])
        pause(4)
    }
}
