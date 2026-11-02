import Foundation

/// Shared between the main app and the Screen Time shield action extension.
public enum ShieldGoalLaunch {
    public static let appGroupSuite = "group.com.morningguard.shared"
    public static let pendingOpenGoalKey = "shieldOpenGoalPending"
    public static let notificationIdentifierPrefix = "morningguard.shield.goal"
    public static let deepLinkURLString = "morningguard://goal"
    public static let notificationUserInfoDeepLinkKey = "mgDeepLink"

    public static func markOpenGoalPending() {
        UserDefaults(suiteName: appGroupSuite)?.set(true, forKey: pendingOpenGoalKey)
    }

    /// Returns whether a pending open was consumed (cleared from app group).
    public static func consumeOpenGoalPending() -> Bool {
        guard let defaults = UserDefaults(suiteName: appGroupSuite) else { return false }
        let pending = defaults.bool(forKey: pendingOpenGoalKey)
        if pending {
            defaults.removeObject(forKey: pendingOpenGoalKey)
        }
        return pending
    }

    public static func isGoalDeepLink(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "morningguard" else { return false }
        if url.host?.lowercased() == "goal" { return true }
        return url.path == "/goal"
    }

    public static func deepLinkFromNotificationUserInfo(_ userInfo: [AnyHashable: Any]) -> URL? {
        guard let raw = userInfo[notificationUserInfoDeepLinkKey] as? String else { return nil }
        let url = URL(string: raw)
        guard let url, isGoalDeepLink(url) else { return nil }
        return url
    }
}
