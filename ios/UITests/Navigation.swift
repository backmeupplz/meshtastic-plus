import XCTest

/// Moving around Home the same way on every display. Compact widths show Chats and Nodes as tabs (a tab bar on most
/// phones, a floating rail on iPhone Duo's cover display) and push conversations; regular ones (Duo's inner display,
/// iPad in a `TARGETED_DEVICE_FAMILY=1,2` build) switch Chats and Nodes in the sidebar and show the conversation next
/// to it. Either way they're buttons; the sidebar's Chats carries the unread count in its label.
extension XCUIApplication {
    /// An exact label wins: in Indonesian and Vietnamese, Nodes is "Node", which the "Node Demo" device row also starts with.
    func tab(_ name: String) {
        let exact = buttons.matching(NSPredicate(format: "label == %@", name)).firstMatch
        (exact.exists ? exact : buttons.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch).tap()
    }

    /// Hides the keyboard in a conversation: tapping its date tag (`today`, in the app's language) does, or, when big text
    /// or a short screen pushed that off screen, dragging the messages (the one containing `text`) down.
    func hideKeyboard(above text: String, today label: String = "Today") {
        let today = staticTexts[label]
        if today.exists && today.isHittable { today.tap() } else { scrollViews.containing(.staticText, identifier: text).firstMatch.swipeDown() }
    }

    /// Leaves a pushed conversation on compact widths; on regular ones it sits next to the list, so there's nothing to leave.
    func backToList() {
        guard !segmentedControls.firstMatch.exists else { return }
        let back = buttons["BackButton"].firstMatch // the navigation bar's, or the floating one on Duo's cover display
        (back.exists ? back : navigationBars.buttons.element(boundBy: 0)).tap()
    }
}
