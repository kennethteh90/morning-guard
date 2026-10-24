import Foundation
import MorningGuardCore

/// Seeds demo data for App Store screenshots (mirrors Android `ScreenshotDemoSeeder`).
@MainActor
enum ScreenshotDemoSeeder {
    static func seedIfNeeded(
        settingsStore: SettingsStore,
        highlightStore: BibleHighlightStore
    ) {
        guard MGDebugFlags.screenshotMode else { return }

        let window = DebugWindowPresets.includingNow()
        settingsStore.updateUserSettings { settings in
            var copy = settings
            copy.onboardingCompleted = true
            copy.morningWindow = window
            copy.morningGoal = MorningGoalConfig(
                type: .bibleChapter,
                bibleTranslation: "web",
                bibleBook: "Psalm",
                bibleChapter: 23
            )
            return copy
        }

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let calendar = Calendar.current
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"

        var history: [MorningDayRecord] = []
        for offset in -6...0 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: Date()) else { continue }
            let dateKey = formatter.string(from: day)
            let outcome: MorningDayOutcome = offset == -2 ? .emergencyBypass : .goalCompleted
            history.append(
                MorningDayRecord(
                    dateKey: dateKey,
                    outcome: outcome,
                    recordedAtEpochMs: nowMs + Int64(offset) * 1000,
                    timeOnGoalSeconds: outcome == .goalCompleted ? 420 : 0
                )
            )
        }

        let todayKey = formatter.string(from: Date())
        settingsStore.updateGoalProgress { store in
            var copy = store
            copy.morningHistory = history
            copy.progress = MorningGoalProgress(
                dateKey: todayKey,
                config: MorningGoalConfig(type: .bibleChapter, bibleBook: "Psalm", bibleChapter: 23),
                startedAtEpochMs: nowMs - 60_000,
                completedAtEpochMs: nil,
                stepsAccumulated: 0,
                onScreenSeconds: 45
            )
            return copy
        }

        if highlightStore.highlights.isEmpty {
            let sample = BibleHighlight(
                id: 1,
                remoteId: 0,
                translationId: "web",
                book: "Psalm",
                chapter: 23,
                label: "Psalm 23:1",
                excerpt: "The Lord is my shepherd; I shall not want.",
                verse: 1,
                verseEnd: nil,
                verseNumbers: "1",
                colorKey: BibleHighlightColors.defaultKey,
                note: "",
                selectionStart: nil,
                selectionEnd: nil,
                createdAtEpochMs: nowMs
            )
            highlightStore.restoreHighlight(sample)
        }
    }
}
