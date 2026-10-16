import XCTest

/// App Store screenshots from the demo node: `TEST_RUNNER_SHOTS=<dir> xcodebuild test -only-testing:UITests/Screenshots` (see ../screenshots.sh).
final class Screenshots: XCTestCase {
    func testScreenshots() throws {
        guard let dir = ProcessInfo.processInfo.environment["SHOTS"] else { throw XCTSkip("set TEST_RUNNER_SHOTS to save screenshots") }
        func shot(_ name: String) throws {
            sleep(1) // let animations settle
            try XCUIScreen.main.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
        let app = XCUIApplication()
        app.launchArguments = ["-uitest"]
        app.launch()
        app.buttons["Agree"].tap()
        try shot("1-connect")
        app.buttons["No radio yet? Try the demo"].tap()
        XCTAssert(app.staticTexts["Hiking"].waitForExistence(timeout: 15))
        try shot("2-chats")
        app.staticTexts["Hiking"].tap()
        XCTAssert(app.staticTexts["Copy. Dinner at 7 at camp."].waitForExistence(timeout: 10))
        let field = app.textViews.firstMatch.exists ? app.textViews.firstMatch : app.textFields.firstMatch
        field.tap()
        field.typeText("On my way, 20 minutes out")
        app.buttons["Send"].tap()
        XCTAssert(app.staticTexts["Got it 👍"].waitForExistence(timeout: 10))
        app.staticTexts["Today"].tap() // hide the keyboard
        try shot("3-room")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.tabBars.buttons["Nodes"].tap()
        XCTAssert(app.staticTexts["Maya's Handheld"].waitForExistence(timeout: 5))
        try shot("4-nodes")
        app.buttons["Settings"].tap()
        app.staticTexts["Node settings"].tap()
        XCTAssert(app.staticTexts["Heltec Mesh Node T114"].waitForExistence(timeout: 5))
        try shot("5-settings")
    }
}
