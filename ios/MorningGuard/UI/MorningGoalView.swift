import MorningGuardCore
import SwiftUI

struct MorningGoalView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.scenePhase) private var scenePhase

    let onCompleted: () -> Void

    @State private var todaySteps: Int?
    @State private var tickerTask: Task<Void, Never>?

    private var config: MorningGoalConfig {
        appState.settingsStore.userSettings.morningGoal
    }

    private var progress: MorningGoalProgress? {
        appState.settingsStore.goalProgressStore.progress
    }

    var body: some View {
        Group {
            if config.type == .bibleChapter {
                bibleGoalLayout
            } else {
                nonBibleLayout
            }
        }
        .background(MGTheme.background)
        .navigationTitle("Morning goal")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if config.type == .bibleChapter {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 2) {
                        Text(headerTitle)
                            .font(.subheadline.weight(.semibold))
                        Text(bibleToolbarSubtitle)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .task { await onAppearWork() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                startTickerIfNeeded()
            } else {
                stopTicker()
            }
        }
        .onDisappear {
            stopTicker()
            if progress?.isComplete == true {
                appState.finalizeBibleReadingAfterGoalSessionIfNeeded()
            }
        }
    }

    private var bibleGoalLayout: some View {
        VStack(spacing: 0) {
            if appState.gateService.evaluation.state == .inactive {
                outsideWindowBanner
                    .padding(.horizontal, MGTheme.standardPadding)
                    .padding(.top, 8)
            }
            BibleReadingScreen(
                browseOnly: false,
                showsGoalProgress: true,
                elapsedSeconds: progress?.onScreenSeconds ?? 0,
                requiredSeconds: 60
            )
            completionFooter
                .padding(MGTheme.standardPadding)
            if MGDebugFlags.showDebugPanels {
                goalDebugFooter
                    .padding(.horizontal, MGTheme.standardPadding)
                    .padding(.bottom, MGTheme.standardPadding)
            }
        }
    }

    private var nonBibleLayout: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if appState.gateService.evaluation.state == .inactive {
                    outsideWindowBanner
                }
                header
                switch config.type {
                case .timedFocus:
                    timedFocusBody
                case .stepCount:
                    stepBody
                case .bibleChapter:
                    EmptyView()
                }
                completionFooter
                if MGDebugFlags.showDebugPanels {
                    goalDebugFooter
                }
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var outsideWindowBanner: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Outside morning window")
                    .font(.headline)
                Text("Reading still works here. The home screen only offers the morning goal while the gate is locked.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if MGDebugFlags.showDebugPanels {
                    Button("Activate test window") {
                        appState.debugActivateTestWindow()
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }

    private var goalDebugFooter: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Debug")
                    .font(.caption.bold())
                Text("Gate: \(appState.gateService.evaluation.state.rawValue) (\(appState.gateService.evaluation.reason))")
                    .font(.caption2.monospaced())
                Text("Can complete: \(appState.gateService.canComplete(currentSteps: todaySteps))")
                    .font(.caption2.monospaced())
                HStack {
                    Button("Test window") { appState.debugActivateTestWindow() }
                    Button("Complete goal") { appState.debugForceCompleteGoal() }
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private var header: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 6) {
                Text(headerTitle)
                    .font(MGTheme.titleFont)
                Text(headerSubtitle)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var timedFocusBody: some View {
        VStack(spacing: 12) {
            Text(timerLabel)
                .font(.system(.title, design: .rounded).monospacedDigit())
            Text("Keep this screen open until the timer finishes.")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }

    private var stepBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            if appState.healthKitStepReader.isAvailable {
                Text("Steps today: \(todaySteps ?? 0) / \(config.stepTarget)")
                    .font(.title2.monospacedDigit())
                Button("Refresh HealthKit steps") {
                    Task { await refreshSteps() }
                }
            } else {
                Text("HealthKit is not available on this device.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var completionFooter: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let progress {
                if progress.isComplete {
                    goalCompleteCard
                } else {
                    Text(progressLabel(progress: progress))
                        .font(.callout)
                        .foregroundStyle(.secondary)
                    if appState.gateService.canComplete(currentSteps: todaySteps) {
                        Button {
                            completeGoalExplicitly()
                        } label: {
                            Label("Complete morning goal", systemImage: "checkmark.circle.fill")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(MGTheme.accent)
                    } else {
                        Text(completionHint)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var goalCompleteCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Morning goal complete", systemImage: "sun.max.fill")
                .font(.headline)
                .foregroundStyle(MGTheme.accent)
            Text(config.type == .bibleChapter
                ? "Your apps are unlocked. Keep reading here or go home when you're ready."
                : "Your apps are unlocked for today.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Back to home") {
                leaveGoalScreen()
            }
            .buttonStyle(.borderedProminent)
            .tint(MGTheme.accent)
            .frame(maxWidth: .infinity)
        }
        .padding(MGTheme.standardPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MGTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: MGTheme.cardCornerRadius, style: .continuous))
    }

    private var completionHint: String {
        switch config.type {
        case .bibleChapter:
            return "Stay on this screen until you've read for at least 60 seconds, then tap Complete morning goal."
        case .timedFocus:
            return "Keep this screen open until the timer finishes."
        case .stepCount:
            return "Walk until you reach your step target, then tap Complete morning goal."
        }
    }

    private func completeGoalExplicitly() {
        appState.gateService.markComplete()
        appState.shieldController.refreshShields()
        MGHaptics.success()
        stopTicker()
    }

    private func leaveGoalScreen() {
        appState.finalizeBibleReadingAfterGoalSessionIfNeeded()
        onCompleted()
    }

    private var headerTitle: String {
        switch config.type {
        case .bibleChapter: "Read your chapter"
        case .timedFocus: "Timed focus"
        case .stepCount: "Step count"
        }
    }

    private var bibleToolbarSubtitle: String {
        "Chapter \(config.bibleChapter) of \(BibleCanon.chapterCount(bookName: config.bibleBook)) · stay on this screen"
    }

    private var headerSubtitle: String {
        switch config.type {
        case .bibleChapter:
            return "Stay on this screen while you read. Chapter \(config.bibleChapter) of \(BibleCanon.chapterCount(bookName: config.bibleBook))."
        case .timedFocus:
            return "\(config.focusMinutes) minutes with Morning Guard open."
        case .stepCount:
            return "Walk \(config.stepTarget) steps while this screen is open."
        }
    }

    private var timerLabel: String {
        let elapsed = progress?.onScreenSeconds ?? 0
        let required = config.type == .timedFocus ? config.focusMinutes * 60 : 60
        let remaining = max(0, required - elapsed)
        let minutes = remaining / 60
        let seconds = remaining % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }

    private func progressLabel(progress: MorningGoalProgress) -> String {
        switch config.type {
        case .bibleChapter:
            return "Reading time: \(progress.onScreenSeconds)s / 60s"
        case .timedFocus:
            return "Focus time: \(progress.onScreenSeconds)s / \(config.focusMinutes * 60)s"
        case .stepCount:
            return "Goal progress: \(progress.stepsAccumulated) / \(config.stepTarget) steps"
        }
    }

    private func onAppearWork() async {
        _ = appState.gateService.startGoalIfNeeded()
        if config.type == .stepCount {
            try? await appState.healthKitStepReader.requestAuthorization()
            await refreshSteps()
        }
        startTickerIfNeeded()
    }

    private func refreshSteps() async {
        do {
            let steps = try await appState.healthKitStepReader.todayStepCount()
            todaySteps = steps
            appState.gateService.syncStepCount(steps)
            appState.shieldController.refreshShields()
        } catch {
            // HealthKit errors are shown in step body when needed.
        }
    }

    private func startTickerIfNeeded() {
        guard config.type != .stepCount else { return }
        stopTicker()
        tickerTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                await MainActor.run {
                    appState.gateService.incrementOnScreenSeconds()
                    appState.shieldController.refreshShields()
                }
            }
        }
    }

    private func stopTicker() {
        tickerTask?.cancel()
        tickerTask = nil
    }
}
