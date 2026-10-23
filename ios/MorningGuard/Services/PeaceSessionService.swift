import Foundation
import MorningGuardCore

@MainActor
final class PeaceSessionService {
    private let settingsStore: SettingsStore
    private let gateService: GateService

    init(settingsStore: SettingsStore, gateService: GateService) {
        self.settingsStore = settingsStore
        self.gateService = gateService
    }

    var activeUntilEpochMs: Int64? {
        let nowMs = Self.nowMs()
        return PeaceSessionPolicy.activeUntil(
            storedUntilEpochMs: settingsStore.goalProgressStore.peaceSessionUntilEpochMs,
            nowMs: nowMs
        )
    }

    func reconcileStoredPeaceSession() {
        let nowMs = Self.nowMs()
        guard let until = settingsStore.goalProgressStore.peaceSessionUntilEpochMs else { return }
        if nowMs >= until {
            settingsStore.updateGoalProgress { store in
                var copy = store
                copy.peaceSessionUntilEpochMs = nil
                return copy
            }
            gateService.refresh()
        }
    }

    @discardableResult
    func startOrExtendOneHour() -> Int64 {
        reconcileStoredPeaceSession()
        let nowMs = Self.nowMs()
        let store = settingsStore.goalProgressStore
        let peaceActive = PeaceSessionPolicy.activeUntil(storedUntilEpochMs: store.peaceSessionUntilEpochMs, nowMs: nowMs) != nil

        if !peaceActive, PeaceSessionPolicy.isMorningGoalActive(evaluation: gateService.evaluation) {
            return store.peaceSessionUntilEpochMs ?? nowMs
        }

        let base = PeaceSessionPolicy.activeUntil(storedUntilEpochMs: store.peaceSessionUntilEpochMs, nowMs: nowMs) ?? nowMs
        let until = base + PeaceSessionPolicy.sessionDurationMs
        settingsStore.updateGoalProgress { store in
            var copy = store
            copy.peaceSessionUntilEpochMs = until
            return copy
        }
        gateService.refresh()
        return until
    }

    func endPeaceSession() {
        settingsStore.updateGoalProgress { store in
            var copy = store
            copy.peaceSessionUntilEpochMs = nil
            return copy
        }
        gateService.refresh()
    }

    private static func nowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}
