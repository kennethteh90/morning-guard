import DeviceActivity
import FamilyControls
import Foundation
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
        refreshShields()
    }

    override func eventDidReachThreshold(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventDidReachThreshold(event, activity: activity)
        let state = SharedShieldStateReader.load()
        if ShieldSessionPolicy.shouldReapplyAfterEvent(
            gateState: state.gateState,
            hasSelection: state.hasSelection
        ), let selection = state.selection {
            applier.apply(selection: selection)
        }
    }

    private func refreshShields() {
        let state = SharedShieldStateReader.load()
        if ShieldSessionPolicy.shouldClearShields(gateState: state.gateState) {
            applier.clear()
            return
        }
        if ShieldSessionPolicy.shouldApplyShields(
            gateState: state.gateState,
            isAuthorized: state.isFamilyControlsAuthorized,
            hasSelection: state.hasSelection
        ), let selection = state.selection {
            applier.apply(selection: selection)
        } else {
            applier.clear()
        }
    }
}
