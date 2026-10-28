import XCTest

/// App Store screenshots from the demo node: `TEST_RUNNER_SHOTS=<dir> xcodebuild test -only-testing:UITests/Screenshots` (see ../screenshots.sh).
/// Works on any simulator, both iPhone Duo displays included (see Navigation.swift). `TEST_RUNNER_STORE_LANG` runs the app
/// in that language (one in Localizable.xcstrings, e.g. de, pt-BR or zh-Hans; default en), finding its controls by their
/// translations, so ../screenshots.sh can take every store language. `TEST_RUNNER_SHOTS_ARGS` adds launch arguments.
final class Screenshots: XCTestCase {
    let env = ProcessInfo.processInfo.environment
    lazy var language = env["STORE_LANG"] ?? "en"

    /// The app's own translation of an English string, read from the string catalog next to this file
    lazy var catalog: [String: Any] = {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("MeshPlus/Localizable.xcstrings")
        let json = (try? JSONSerialization.jsonObject(with: Data(contentsOf: url))) as? [String: Any]
        return json?["strings"] as? [String: Any] ?? [:]
    }()

    func t(_ english: String) -> String {
        let entry = catalog[english] as? [String: Any]
        let localized = (entry?["localizations"] as? [String: Any])?[language] as? [String: Any]
        return (localized?["stringUnit"] as? [String: Any])?["value"] as? String ?? english
    }

    func testScreenshots() throws {
        guard let dir = env["SHOTS"] else { throw XCTSkip("set TEST_RUNNER_SHOTS to save screenshots") }
        continueAfterFailure = false
        try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
        func shot(_ name: String) throws {
            sleep(1) // let animations settle
            try XCUIScreen.main.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
        let app = XCUIApplication()
        let extra = env["SHOTS_ARGS"]?.split(separator: " ").map(String.init) ?? []
        let languageArgs = language == "en" ? [] : ["-AppleLanguages", "(\(language))", "-AppleLocale", language.replacingOccurrences(of: "-", with: "_")]
        app.launchArguments = ["-uitest"] + languageArgs + extra
        app.launch()
        // the demo room's name as the demo node has it (Demo.swift): no spaces, at most 11 bytes
        var room = t("Hiking").replacingOccurrences(of: " ", with: "")
        while room.utf8.count > 11 { room.removeLast() }
        let dinner = t("Copy. Dinner at 7 at camp."), reply = t("Got it 👍")
        app.buttons[t("Agree")].tap()
        try shot("1-connect")
        app.buttons[t("No radio yet? Try the demo")].tap()
        XCTAssert(app.staticTexts[room].waitForExistence(timeout: 15))
        try shot("2-chats")
        app.staticTexts[room].tap()
        XCTAssert(app.staticTexts[dinner].waitForExistence(timeout: 10))
        let field = app.textViews.firstMatch.exists ? app.textViews.firstMatch : app.textFields.firstMatch
        field.tap()
        field.typeText(language == "en" ? "On my way, 20 minutes out" : t("On my way."))
        app.buttons[t("Send")].tap()
        XCTAssert(app.staticTexts[reply].waitForExistence(timeout: 10))
        app.hideKeyboard(above: reply, today: t("Today"))
        // the list can stay scrolled up where the keyboard pushed it: bring the newest message back above the message field
        let shown = { app.staticTexts[reply].isHittable && app.staticTexts[reply].frame.maxY + 30 <= field.frame.minY } // its time sits below the text
        for _ in 0..<5 where !shown() {
            app.scrollViews.containing(.staticText, identifier: reply).firstMatch.swipeUp()
            sleep(1)
        }
        XCTAssert(shown(), "the reply is hidden")
        try shot("3-room")
        app.backToList()
        app.tab(t("Nodes"))
        XCTAssert(app.staticTexts[t("Maya's Handheld")].waitForExistence(timeout: 5))
        try shot("4-nodes")
        app.buttons[t("Settings")].tap()
        app.staticTexts[t("Node settings")].tap()
        XCTAssert(app.staticTexts["Heltec Mesh Node T114"].waitForExistence(timeout: 5))
        try shot("5-settings")
    }
}
