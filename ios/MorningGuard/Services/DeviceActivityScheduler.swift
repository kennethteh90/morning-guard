import Foundation
import MorningGuardCore

#if canImport(DeviceActivity)
import DeviceActivity
#endif

#if canImport(DeviceActivity)
extension DeviceActivityName {
    static let morningGate = Self("morningGate")
}
#endif

@MainActor
final class DeviceActivityScheduler {
    #if canImport(DeviceActivity)
    private let center = DeviceActivityCenter()
    #endif

    func reschedule(for window: MorningWindow) {
        #if canImport(DeviceActivity)
        center.stopMonitoring([.morningGate])
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: window.startHour, minute: window.startMinute),
            intervalEnd: DateComponents(hour: window.endHour, minute: window.endMinute),
            repeats: true
        )
        do {
            try center.startMonitoring(.morningGate, during: schedule)
        } catch {
            // Monitor extension may not be installed yet during early development.
        }
        #endif
    }

    func stop() {
        #if canImport(DeviceActivity)
        center.stopMonitoring([.morningGate])
        #endif
    }
}
