import Foundation

/// The App Store's own rating prompt, never a question of ours first: after 20 sent messages, once the app was opened
/// on 3 days and first used 3+ days ago. At most twice per install, the second time 120+ days later on a newer build,
/// since iOS doesn't say whether the user rated. Same rule as the Android app; kept on this phone, nothing is sent anywhere.
enum RatingPrompt {
    static let day: Double = 86_400
    private static let defaults = UserDefaults.standard
    private static let started = Date().timeIntervalSince1970

    static func due(now: Double, first: Double, days: Int, sent: Int, asks: Int, lastAsk: Double, lastVersion: Int, version: Int) -> Bool {
        days >= 3 && now - first >= 3 * day && sent >= 20 && (asks == 0 || asks == 1 && now - lastAsk >= 120 * day && version > lastVersion)
    }

    /// The app is on screen: counts the days it's used on.
    static func used() {
        let today = Calendar.current.startOfDay(for: Date()).timeIntervalSince1970
        if defaults.double(forKey: "rateDay") == today { return }
        if defaults.object(forKey: "rateFirst") == nil { defaults.set(Date().timeIntervalSince1970, forKey: "rateFirst") }
        defaults.set(today, forKey: "rateDay")
        defaults.set(defaults.integer(forKey: "rateDays") + 1, forKey: "rateDays")
    }

    static func sent() { defaults.set(defaults.integer(forKey: "rateSent") + 1, forKey: "rateSent") }

    /// Back from a chat to the list: true when the prompt should show now, and counts it as shown.
    /// Not after a message of ours failed since the app started.
    static func ask() -> Bool {
        let now = Date().timeIntervalSince1970
        let version = Int(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "") ?? 0
        if Mesh.shared.messages.contains(where: { $0.mine && $0.status == "✗" && $0.time >= started * 1000 }) { return false }
        let asks = defaults.integer(forKey: "rateAsks")
        guard due(now: now, first: defaults.object(forKey: "rateFirst") as? Double ?? now, days: defaults.integer(forKey: "rateDays"),
                  sent: defaults.integer(forKey: "rateSent"), asks: asks, lastAsk: defaults.double(forKey: "rateLastAsk"),
                  lastVersion: defaults.integer(forKey: "rateVersion"), version: version) else { return false }
        defaults.set(asks + 1, forKey: "rateAsks")
        defaults.set(now, forKey: "rateLastAsk")
        defaults.set(version, forKey: "rateVersion")
        return true
    }
}
