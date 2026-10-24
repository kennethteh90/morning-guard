import Foundation

public enum RecoveryPasswordPromptPolicy {
    private static let rePromptAfterDismissMs: Int64 = 7 * 24 * 60 * 60 * 1000
    private static let noteMilestones: Set<Int> = [1, 3, 5, 10]

    public static func shouldShowDedicatedSheet(
        settings: UserSettings,
        hasRecoveryPassword: Bool,
        signedIn: Bool,
        cloudRecoveryRequired: Bool
    ) -> Bool {
        if !signedIn || settings.dataStorageMode != .cloudSync { return false }
        if hasRecoveryPassword { return false }
        if cloudRecoveryRequired { return true }
        return !settings.recoveryPasswordPromptShownAfterSignIn
    }

    public static func shouldShowPeriodicSheet(
        settings: UserSettings,
        hasRecoveryPassword: Bool,
        signedIn: Bool,
        nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> Bool {
        if !signedIn || settings.dataStorageMode != .cloudSync { return false }
        if hasRecoveryPassword { return false }
        if settings.recoveryPasswordPromptShownAfterSignIn,
           settings.recoveryPasswordLaterCount > 0,
           nowEpochMs - settings.recoveryPasswordPromptLastDismissedEpochMs >= rePromptAfterDismissMs {
            return true
        }
        return noteMilestones.contains(settings.noteSaveCountForRecoveryPrompt)
    }

    public static func shouldShowBanner(
        settings: UserSettings,
        hasRecoveryPassword: Bool,
        signedIn: Bool
    ) -> Bool {
        if !signedIn || settings.dataStorageMode != .cloudSync { return false }
        return !hasRecoveryPassword
    }
}
