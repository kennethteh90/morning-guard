import Foundation
import MorningGuardCore

/// Deep-link style scenes for `-MGScreenshotMode` guide captures (`-MGGuideScene <name>`).
@MainActor
enum ScreenshotGuideScene {
    static func nameFromLaunchArguments() -> String? {
        guard MGDebugFlags.screenshotMode else { return nil }
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "-MGGuideScene"), index + 1 < args.count else {
            return nil
        }
        return args[index + 1].lowercased()
    }

    static func applyIfNeeded(
        scene: String?,
        settingsStore: SettingsStore,
        highlightStore: BibleHighlightStore
    ) {
        guard MGDebugFlags.screenshotMode else { return }
        switch scene {
        case "focus":
            ScreenshotDemoSeeder.forcedOnboardingStep = 5
            settingsStore.updateUserSettings { settings in
                var copy = settings
                copy.onboardingCompleted = false
                return copy
            }
        case "apps":
            ScreenshotDemoSeeder.forcedOnboardingStep = 3
            settingsStore.updateUserSettings { settings in
                var copy = settings
                copy.onboardingCompleted = false
                return copy
            }
        case "settings", "goal", "home", nil:
            ScreenshotDemoSeeder.forcedOnboardingStep = nil
            ScreenshotDemoSeeder.seedIfNeeded(settingsStore: settingsStore, highlightStore: highlightStore)
        default:
            ScreenshotDemoSeeder.seedIfNeeded(settingsStore: settingsStore, highlightStore: highlightStore)
        }
    }
}
