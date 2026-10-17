import DeviceActivity
import FamilyControls
import ManagedSettings
import MorningGuardCore

final class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    private let applier = ShieldApplier()

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        refreshShields()
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        // Scheduled morning window ended; remove shields. App re-applies on next refresh if still locked.
        applier.clear()
    }

    override func eventDidReachThreshold(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventDidReachThreshold(event, activity: activity)
        let state = SharedShieldStateReader.load()
        if ShieldSessionPolicy.shouldReapplyAfterEvent(
            shieldsActive: state.shieldsActive,
            hasSelection: state.hasSelection
        ), let selection = state.selection {
            applier.apply(selection: selection)
        }
    }

    private func refreshShields() {
        let state = SharedShieldStateReader.load()
        guard ShieldSessionPolicy.shouldApplyShieldsFromSharedState(
            shieldsActive: state.shieldsActive,
            hasSelection: state.hasSelection
        ), let selection = state.selection else {
            return
        }
        applier.apply(selection: selection)
    }
}
