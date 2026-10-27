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
        // UI tests: no transitions to wait out (XCUITest waits for the app to go idle after every step).
        if CommandLine.arguments.contains("-uitest") { UIView.setAnimationsEnabled(false) }
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
        let mesh = Mesh.shared
        if !mesh.agreedTerms { TermsView() }
        else if !mesh.ready { SetupView() }
        else if mesh.region == 0 { RegionView(editing: false) }
        else if mesh.askName { NameView(editing: false) }
        else { HomeView() }
    }
}
