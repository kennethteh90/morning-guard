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

    public static func shouldClearShields(gateState: GateState) -> Bool {
        gateState != .locked
    }

    /// Re-apply after usage threshold events while the gate is still locked.
    public static func shouldReapplyAfterEvent(gateState: GateState, hasSelection: Bool) -> Bool {
        gateState == .locked && hasSelection
    }
}
