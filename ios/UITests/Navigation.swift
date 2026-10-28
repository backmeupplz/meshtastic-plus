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

    /// Whether the screen shows the bubble of the incoming message `text` where its accessibility frame says, ending above
    /// the message `field`, judged from a screenshot: on Duo's cover display the frames once put a message above the field
    /// while it was drawn behind it (#249). Down a column inside the bubble's padding (left of the text, RTL too), the rows
    /// right above the composer must be the background, and the first color above them must cover the text's rows. Made
    /// for the dark store shots (in light mode the bar is close to the background) and for texts wider than their time,
    /// which otherwise pushes the text right of the bubble's padding.
    func drawn(_ text: XCUIElement, above field: XCUIElement) -> Bool {
        guard let image = XCUIScreen.main.screenshot().image.cgImage else { return false }
        let window = windows.firstMatch.frame
        let scale = CGFloat(image.width) / window.width
        var pixels = [UInt8](repeating: 0, count: image.width * image.height * 4)
        guard let context = CGContext(data: &pixels, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: image.width * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        func color(_ x: CGFloat, _ y: CGFloat) -> [Int] {
            let i = (Int(y * scale) * image.width + Int(x * scale)) * 4
            return [Int(pixels[i]), Int(pixels[i + 1]), Int(pixels[i + 2])]
        }
        func same(_ a: [Int], _ b: [Int]) -> Bool { zip(a, b).allSatisfy { abs($0 - $1) < 6 } }
        let x = text.frame.minX - 8 // the bubble pads its text by 14 pt
        let barTop = field.frame.minY - 17 // the field's own 9 pt padding, then the composer's 8 pt
        guard text.frame.minX > 24, barTop > 60, barTop < window.maxY else { return false }
        let background = color(text.frame.minX - 22, text.frame.midY) // 8 pt outside the bubble: its row has nothing else
        guard !same(color(x, barTop + 4), background) else { return false } // the composer itself isn't background
        var y = barTop - 2
        while y > barTop - 8 { if !same(color(x, y), background) { return false }; y -= 1 }
        while y > 3 && same(color(x, y), background) { y -= 1 }
        let bottom = y, bubble = color(x, bottom - 3) // past its antialiased edge
        y = bottom - 3
        while y > 3 && same(color(x, y - 1), bubble) { y -= 1 }
        return !same(bubble, background) && y <= text.frame.minY && bottom >= text.frame.maxY
    }

    /// Leaves a pushed conversation on compact widths; on regular ones it sits next to the list, so there's nothing to leave.
    func backToList() {
        guard !segmentedControls.firstMatch.exists else { return }
        let back = buttons["BackButton"].firstMatch // the navigation bar's, or the floating one on Duo's cover display
        (back.exists ? back : navigationBars.buttons.element(boundBy: 0)).tap()
    }
}
