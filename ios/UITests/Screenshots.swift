import XCTest

/// App Store screenshots from the demo node: `TEST_RUNNER_SHOTS=<dir> xcodebuild test -only-testing:UITests/Screenshots` (see ../screenshots.sh).
/// Works on any simulator, both iPhone Duo displays included (see Navigation.swift). `TEST_RUNNER_SHOTS_ARGS` adds launch
/// arguments, e.g. `-AppleLanguages (de)` or `-AppleTextDirection YES -NSForceRightToLeftWritingDirection YES` for RTL.
final class Screenshots: XCTestCase {
    func testScreenshots() throws {
        guard let dir = ProcessInfo.processInfo.environment["SHOTS"] else { throw XCTSkip("set TEST_RUNNER_SHOTS to save screenshots") }
        func shot(_ name: String) throws {
            sleep(1) // let animations settle
            try XCUIScreen.main.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
        let app = XCUIApplication()
        let extra = ProcessInfo.processInfo.environment["SHOTS_ARGS"]?.split(separator: " ").map(String.init) ?? []
        app.launchArguments = ["-uitest"] + extra
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
        app.hideKeyboard(above: "Got it 👍")
        try shot("3-room")
        app.backToList()
        app.tab("Nodes")
        XCTAssert(app.staticTexts["Maya's Handheld"].waitForExistence(timeout: 5))
        try shot("4-nodes")
        app.buttons["Settings"].tap()
        app.staticTexts["Node settings"].tap()
        XCTAssert(app.staticTexts["Heltec Mesh Node T114"].waitForExistence(timeout: 5))
        try shot("5-settings")
    }
}
