import ManagedSettings
import MorningGuardCore
import UserNotifications

final class ShieldActionExtension: ShieldActionDelegate {
    private func handlePrimaryButton(completionHandler: @escaping (ShieldActionResponse) -> Void) {
        // Screen Time shield extensions cannot open the parent app. Schedule a local notification
        // the user can tap (deep link) and dismiss the shield.
        ShieldGoalLaunch.markOpenGoalPending()
        scheduleOpenGoalNotification()
        completionHandler(.close)
    }

    private func scheduleOpenGoalNotification() {
        let content = UNMutableNotificationContent()
        content.title = "Morning Guard"
        content.body = "Tap to open your morning goal."
        content.sound = .default
        content.userInfo = [
            ShieldGoalLaunch.notificationUserInfoDeepLinkKey: ShieldGoalLaunch.deepLinkURLString,
        ]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.15, repeats: false)
        let identifier = "\(ShieldGoalLaunch.notificationIdentifierPrefix).\(UUID().uuidString)"
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    override func handle(
        action: ShieldAction,
        for application: ApplicationToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        switch action {
        case .primaryButtonPressed:
            handlePrimaryButton(completionHandler: completionHandler)
        case .secondaryButtonPressed:
            completionHandler(.close)
        @unknown default:
            completionHandler(.close)
        }
    }

    override func handle(
        action: ShieldAction,
        for webDomain: WebDomainToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        switch action {
        case .primaryButtonPressed:
            handlePrimaryButton(completionHandler: completionHandler)
        case .secondaryButtonPressed:
            completionHandler(.close)
        @unknown default:
            completionHandler(.close)
        }
    }

    override func handle(
        action: ShieldAction,
        for category: ActivityCategoryToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        switch action {
        case .primaryButtonPressed:
            handlePrimaryButton(completionHandler: completionHandler)
        case .secondaryButtonPressed:
            completionHandler(.close)
        @unknown default:
            completionHandler(.close)
        }
    }
}
