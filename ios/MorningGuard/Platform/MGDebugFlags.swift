import Foundation

/// Debug-only behavior (mirrors Android `AppDebugFlags`). Stripped from Release UX except Simulator onboarding relaxations.
enum MGDebugFlags {
    /// Set `true` in Info.plist for screenshot automation builds (hides debug panels while still DEBUG).
    static var screenshotMode: Bool {
        if ProcessInfo.processInfo.arguments.contains("-MGScreenshotMode") { return true }
        return (Bundle.main.object(forInfoDictionaryKey: "MORNING_GUARD_SCREENSHOT_MODE") as? Bool) == true
    }

    /// Debug cards in Settings, step overlays, etc.
    static var showDebugPanels: Bool {
        #if DEBUG
        return !screenshotMode
        #else
        return false
        #endif
    }

    /// Skip Screen Time onboarding requirements and use relaxed test windows after setup.
    static var relaxesShieldOnboarding: Bool {
        showDebugPanels || MGPlatform.isSimulator
    }

    static var verboseDiagnostics: Bool {
        showDebugPanels
    }
}
