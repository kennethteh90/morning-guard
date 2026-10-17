import Foundation

public enum ShieldSessionPolicy {
    /// Shields apply only while the gate is locked and Family Controls is authorized with a non-empty selection.
    public static func shouldApplyShields(
        gateState: GateState,
        isAuthorized: Bool,
        hasSelection: Bool
    ) -> Bool {
        gateState == .locked && isAuthorized && hasSelection
    }

    /// Device Activity Monitor: main app mirrors `shieldsActive` when gate + auth + selection align.
    public static func shouldApplyShieldsFromSharedState(
        shieldsActive: Bool,
        hasSelection: Bool
    ) -> Bool {
        shieldsActive && hasSelection
    }

    /// When the scheduled morning interval ends, extensions should clear shields (app may re-apply if still locked).
    public static func shouldClearShieldsOnIntervalEnd(shieldsActive: Bool) -> Bool {
        true
    }

    /// Re-apply after usage threshold events while the gate session is still active.
    public static func shouldReapplyAfterEvent(
        shieldsActive: Bool,
        hasSelection: Bool
    ) -> Bool {
        shieldsActive && hasSelection
    }
}
