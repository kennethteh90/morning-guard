import Foundation
import MorningGuardCore
import UserNotifications
#if canImport(UIKit)
import UIKit
#endif

enum ShieldGoalLaunchSource: String {
    case notification
    case appGroupPending
    case deepLink
}

@MainActor
final class ShieldGoalLaunchRouter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = ShieldGoalLaunchRouter()

    private weak var appState: AppState?
    private var delegateInstalled = false

    func bind(appState: AppState) {
        self.appState = appState
        installDelegateIfNeeded()
    }

    func installDelegateIfNeeded() {
        guard !delegateInstalled else { return }
        UNUserNotificationCenter.current().delegate = self
        delegateInstalled = true
    }

    func requestAuthorizationIfNeeded() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined:
            return (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        default:
            return false
        }
    }

    func shieldNotificationsEnabled() async -> Bool {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        default:
            return false
        }
    }

    func openAppNotificationSettings() {
        #if canImport(UIKit)
        guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
        UIApplication.shared.open(url)
        #endif
    }

    func consumePendingAppGroupLaunch() {
        guard ShieldGoalLaunch.consumeOpenGoalPending() else { return }
        appState?.openGoalFromShieldPath(source: .appGroupPending)
    }

    // MARK: - UNUserNotificationCenterDelegate

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if ShieldGoalLaunch.deepLinkFromNotificationUserInfo(notification.request.content.userInfo) != nil {
            completionHandler([.banner, .sound])
        } else {
            completionHandler([])
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        guard ShieldGoalLaunch.deepLinkFromNotificationUserInfo(response.notification.request.content.userInfo) != nil else {
            completionHandler()
            return
        }
        Task { @MainActor in
            _ = ShieldGoalLaunch.consumeOpenGoalPending()
            self.appState?.openGoalFromShieldPath(source: .notification)
            completionHandler()
        }
    }
}
