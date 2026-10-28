import SwiftUI
import UserNotifications

@main
struct MeshPlusApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .onChange(of: phase) { _, p in Mesh.shared.visible = p == .active; if p == .active { RatingPrompt.used() } }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ app: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // UI tests: no transitions to wait out (XCUITest waits for the app to go idle after every step). The store
        // screenshots keep them (`-animations`), to look like the app in use: without them, on the Duo cover display
        // simulator (iOS 27.1) closing the keyboard left a short conversation bottom-aligned to the navigation bar's
        // collapsed inset, which put the newest message behind the message field once the bar came back (#249).
        if CommandLine.arguments.contains("-uitest") && !CommandLine.arguments.contains("-animations") { UIView.setAnimationsEnabled(false) }
        #if DEBUG
        if CommandLine.arguments.contains("-foldtest") { FoldTest.observe() }
        #endif
        _ = Mesh.shared // restores history and reconnects to the last node
        return true
    }

    // Banners show while the app is open too; Notify already skips the conversation on screen.
    func userNotificationCenter(_ c: UNUserNotificationCenter, willPresent n: UNNotification,
                                withCompletionHandler done: @escaping (UNNotificationPresentationOptions) -> Void) {
        done([.banner, .sound])
    }

    func userNotificationCenter(_ c: UNUserNotificationCenter, didReceive r: UNNotificationResponse, withCompletionHandler done: @escaping () -> Void) {
        if let convo = r.notification.request.content.userInfo["convo"] as? String {
            DispatchQueue.main.async { Mesh.shared.pendingConvo = convo }
        }
        done()
    }
}

struct RootView: View {
    var body: some View {
        #if DEBUG
        if CommandLine.arguments.contains("-foldtest") { screen.modifier(FoldTest()) } else { screen }
        #else
        screen
        #endif
    }

    // Layouts follow the width size class (and view geometry), never the device or orientation: iPhone Duo's cover
    // display is compact, its inner one regular, and folding switches between them while the app runs.
    // Nothing draws at the screen edges: those are system bars (on Duo's cover display a floating rail), which iOS 27.1
    // lays out around the reserved regions, so the app doesn't read `reservedRegions(kind:)` itself.
    // TODO(#212): confirm on the unfolded inner display; the simulator can't be scripted into it.
    @ViewBuilder private var screen: some View {
        let mesh = Mesh.shared
        if !mesh.agreedTerms { TermsView() }
        else if !mesh.ready { SetupView() }
        else if mesh.region == 0 { RegionView(editing: false) }
        else if mesh.askName { NameView(editing: false) }
        else { HomeView() }
    }
}

#if DEBUG
/// UI tests (`-foldtest`) fold the phone: each Darwin notification `FoldTest.name` flips the width size class under
/// the whole app, the way folding or unfolding an iPhone Duo does while it runs.
struct FoldTest: ViewModifier {
    static let name = "com.backmeupplz.meshplus.fold"
    @Environment(\.horizontalSizeClass) private var width
    @State private var flipped = false

    func body(content: Content) -> some View {
        content
            .environment(\.horizontalSizeClass, flipped ? (width == .regular ? .compact : .regular) : width)
            .onReceive(NotificationCenter.default.publisher(for: .init(Self.name))) { _ in flipped.toggle() }
    }

    static func observe() {
        CFNotificationCenterAddObserver(CFNotificationCenterGetDarwinNotifyCenter(), nil, { _, _, _, _, _ in
            DispatchQueue.main.async { NotificationCenter.default.post(name: .init(FoldTest.name), object: nil) }
        }, name as CFString, nil, .deliverImmediately)
    }
}
#endif
