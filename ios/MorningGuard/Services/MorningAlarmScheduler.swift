import Foundation
import MorningGuardCore
import UserNotifications

@MainActor
final class MorningAlarmScheduler {
    static let notificationIdentifier = "morningguard.alarm"

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

    func apply(alarm: AlarmSettings) async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.notificationIdentifier])
        guard alarm.enabled else { return }
        _ = await requestAuthorizationIfNeeded()

        var date = DateComponents()
        date.hour = alarm.hour
        date.minute = alarm.minute

        let content = UNMutableNotificationContent()
        content.title = "Morning Guard"
        content.body = "Your morning window is starting — complete your goal to unlock your apps."
        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(dateMatching: date, repeats: true)
        let request = UNNotificationRequest(
            identifier: Self.notificationIdentifier,
            content: content,
            trigger: trigger
        )
        try? await center.add(request)
    }
}
