import XCTest
@testable import MorningGuardCore

final class RecoveryPasswordPromptPolicyTests: XCTestCase {
    private func cloudSettings(
        shown: Bool = false,
        laterCount: Int = 0,
        dismissedMs: Int64 = 0,
        noteCount: Int = 0
    ) -> UserSettings {
        UserSettings(
            dataStorageMode: .cloudSync,
            recoveryPasswordPromptShownAfterSignIn: shown,
            recoveryPasswordLaterCount: laterCount,
            recoveryPasswordPromptLastDismissedEpochMs: dismissedMs,
            noteSaveCountForRecoveryPrompt: noteCount
        )
    }

    func testDedicatedSheetAfterSignIn() {
        let settings = cloudSettings()
        XCTAssertTrue(
            RecoveryPasswordPromptPolicy.shouldShowDedicatedSheet(
                settings: settings,
                hasRecoveryPassword: false,
                signedIn: true,
                cloudRecoveryRequired: false
            )
        )
    }

    func testPeriodicSheetAtNoteMilestone() {
        let settings = cloudSettings(noteCount: 3)
        XCTAssertTrue(
            RecoveryPasswordPromptPolicy.shouldShowPeriodicSheet(
                settings: settings,
                hasRecoveryPassword: false,
                signedIn: true
            )
        )
    }

    func testBannerWhenNoRecoveryPassword() {
        let settings = cloudSettings()
        XCTAssertTrue(
            RecoveryPasswordPromptPolicy.shouldShowBanner(
                settings: settings,
                hasRecoveryPassword: false,
                signedIn: true
            )
        )
    }
}
