import MorningGuardCore
import SwiftUI

struct MorningGoalView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.scenePhase) private var scenePhase

    let onCompleted: () -> Void

    @State private var todaySteps: Int?
    @State private var tickerTask: Task<Void, Never>?
    @State private var earlyCompleteNotice: String?
    @State private var earlyCompleteNoticeTask: Task<Void, Never>?
    @State private var bibleNavigationToolbarHidden = false

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
        .navigationTitle(L10n.tr("goal.nav_title"))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityLabel(L10n.tr("goal.nav_title"))
        .task { await onAppearWork() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                appState.refreshGateAndShields()
                startTickerIfNeeded()
            } else {
                stopTicker()
            }
        }
        .onChange(of: appState.gateService.evaluation) { _, _ in
            startTickerIfNeeded()
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
                showsGoalProgress: false,
                showsTranslationInToolbar: true,
                morningGoalTimerLabel: bibleGoalTimerLabel,
                morningGoalTimerReady: bibleGoalTimerReady,
                collapseToolbarOnScroll: true,
                navigationToolbarHidden: $bibleNavigationToolbarHidden
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
                Text(L10n.tr("goal.outside_window.title"))
                    .font(.headline)
                Text(L10n.tr("goal.outside_window.body"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if MGDebugFlags.showDebugPanels {
                    Button(L10n.tr("goal.activate_test_window")) {
                        appState.debugActivateTestWindow()
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(L10n.tr("a11y.goal.outside_window"))
    }

    private var goalDebugFooter: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 8) {
                Text(L10n.tr("goal.debug.section"))
                    .font(.caption.bold())
                Text(
                    L10n.tr(
                        "goal.debug.gate_format",
                        appState.gateService.evaluation.state.rawValue,
                        appState.gateService.evaluation.reason
                    )
                )
                .font(.caption2.monospaced())
                Text(
                    L10n.tr(
                        "goal.debug.can_complete_format",
                        String(appState.gateService.canComplete(currentSteps: todaySteps))
                    )
                )
                .font(.caption2.monospaced())
                HStack {
                    Button(L10n.tr("goal.debug.test_window")) { appState.debugActivateTestWindow() }
                    Button(L10n.tr("goal.debug.complete_goal")) { appState.debugForceCompleteGoal() }
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
        .accessibilityElement(children: .combine)
        .accessibilityLabel(L10n.tr("a11y.goal.header"))
    }

    private var timedFocusBody: some View {
        VStack(spacing: 12) {
            Text(timerLabel)
                .font(.system(.title, design: .rounded).monospacedDigit())
                .accessibilityLabel(L10n.tr("a11y.goal.timer"))
            Text(L10n.tr("goal.timer.stay_open"))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }

    private var stepBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            if appState.healthKitStepReader.isAvailable {
                Text(L10n.tr("goal.steps.today_format", todaySteps ?? 0, config.stepTarget))
                    .font(.title2.monospacedDigit())
                Button(L10n.tr("goal.steps.refresh")) {
                    Task { await refreshSteps() }
                }
                .accessibilityLabel(L10n.tr("a11y.goal.refresh_steps"))
                .accessibilityHint(L10n.tr("a11y.goal.refresh_steps.hint"))
            } else {
                Text(L10n.tr("goal.steps.unavailable"))
                    .foregroundStyle(.secondary)
            }
        }
    }

    /// Matches Home: morning goal can only be finished while the gate is locked for the morning window.
    private var morningGoalCompletionAllowed: Bool {
        let evaluation = appState.gateService.evaluation
        return evaluation.state == .locked && evaluation.reason == "morning_window"
    }

    private var completionFooter: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let progress {
                if progress.isComplete {
                    goalCompleteCard
                } else if !morningGoalCompletionAllowed {
                    EmptyView()
                } else if config.type == .bibleChapter {
                    if let earlyCompleteNotice {
                        Text(earlyCompleteNotice)
                            .font(.callout)
                            .foregroundStyle(.red)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .transition(.opacity)
                    }
                    Button {
                        attemptCompleteGoal()
                    } label: {
                        Label(L10n.tr("goal.complete_button"), systemImage: "checkmark.circle.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(bibleGoalTimerReady ? MGTheme.accent : Color(.systemGray3))
                    .accessibilityLabel(L10n.tr("a11y.goal.complete"))
                    .accessibilityHint(L10n.tr("a11y.goal.complete.hint"))
                } else {
                    Text(progressLabel(progress: progress))
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(L10n.tr("a11y.goal.progress"))
                    if appState.gateService.canComplete(currentSteps: todaySteps) {
                        Button {
                            completeGoalExplicitly()
                        } label: {
                            Label(L10n.tr("goal.complete_button"), systemImage: "checkmark.circle.fill")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(MGTheme.accent)
                        .accessibilityLabel(L10n.tr("a11y.goal.complete"))
                        .accessibilityHint(L10n.tr("a11y.goal.complete.hint"))
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
            Label(L10n.tr("goal.complete.success_title"), systemImage: "sun.max.fill")
                .font(.headline)
                .foregroundStyle(MGTheme.accent)
            Text(
                config.type == .bibleChapter
                    ? L10n.tr("goal.complete.success_bible")
                    : L10n.tr("goal.complete.success_other")
            )
            .font(.subheadline)
            .foregroundStyle(.secondary)
            Button(L10n.tr("goal.back_home")) {
                leaveGoalScreen()
            }
            .buttonStyle(.borderedProminent)
            .tint(MGTheme.accent)
            .frame(maxWidth: .infinity)
            .accessibilityLabel(L10n.tr("a11y.goal.back_home"))
            .accessibilityHint(L10n.tr("a11y.goal.back_home.hint"))
        }
        .padding(MGTheme.standardPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MGTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: MGTheme.cardCornerRadius, style: .continuous))
    }

    private var completionHint: String {
        switch config.type {
        case .bibleChapter:
            return L10n.tr("goal.hint.bible")
        case .timedFocus:
            return L10n.tr("goal.hint.timed")
        case .stepCount:
            return L10n.tr("goal.hint.steps")
        }
    }

    private func attemptCompleteGoal() {
        guard morningGoalCompletionAllowed else {
            showEarlyCompleteNotice(L10n.tr("goal.error.outside_morning_window"))
            MGHaptics.warning()
            return
        }
        if appState.gateService.canComplete(currentSteps: todaySteps) {
            completeGoalExplicitly()
            return
        }
        guard config.type == .bibleChapter else { return }
        let remaining = max(0, 60 - (progress?.onScreenSeconds ?? 0))
        showEarlyCompleteNotice(L10n.tr("goal.error.reading_seconds_remaining", remaining))
        MGHaptics.warning()
    }

    private func showEarlyCompleteNotice(_ message: String) {
        earlyCompleteNoticeTask?.cancel()
        withAnimation { earlyCompleteNotice = message }
        earlyCompleteNoticeTask = Task {
            try? await Task.sleep(for: .seconds(4))
            await MainActor.run {
                withAnimation { earlyCompleteNotice = nil }
            }
        }
    }

    private func completeGoalExplicitly() {
        earlyCompleteNoticeTask?.cancel()
        earlyCompleteNotice = nil
        appState.gateService.markComplete()
        appState.shieldController.refreshShields()
        MGHaptics.success()
        stopTicker()
        Task { await appState.userDataSync.syncMorningHistoryIfSignedIn() }
    }

    private func leaveGoalScreen() {
        appState.finalizeBibleReadingAfterGoalSessionIfNeeded()
        onCompleted()
    }

    private var headerTitle: String {
        switch config.type {
        case .bibleChapter: L10n.tr("goal.header.bible")
        case .timedFocus: L10n.tr("goal.header.timed")
        case .stepCount: L10n.tr("goal.header.steps")
        }
    }

    private var bibleGoalTimerReady: Bool {
        morningGoalCompletionAllowed && appState.gateService.canComplete(currentSteps: todaySteps)
    }

    private var bibleGoalTimerLabel: String {
        if !morningGoalCompletionAllowed {
            return L10n.tr("goal.timer.outside_window")
        }
        if bibleGoalTimerReady {
            return L10n.tr("goal.timer.reading_ready")
        }
        let remaining = max(0, 60 - (progress?.onScreenSeconds ?? 0))
        let minutes = remaining / 60
        let seconds = remaining % 60
        return L10n.tr("goal.timer.reading_remaining_format", minutes, seconds)
    }

    private var headerSubtitle: String {
        switch config.type {
        case .bibleChapter:
            return L10n.tr(
                "goal.detail.bible_format",
                config.bibleChapter,
                BibleCanon.chapterCount(bookName: config.bibleBook)
            )
        case .timedFocus:
            return L10n.tr("goal.detail.timed_format", config.focusMinutes)
        case .stepCount:
            return L10n.tr("goal.detail.steps_format", config.stepTarget)
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
            return L10n.tr("goal.progress.reading_format", progress.onScreenSeconds)
        case .timedFocus:
            return L10n.tr("goal.progress.focus_format", progress.onScreenSeconds, config.focusMinutes * 60)
        case .stepCount:
            return L10n.tr("goal.progress.steps_format", progress.stepsAccumulated, config.stepTarget)
        }
    }

    private func onAppearWork() async {
        appState.refreshGateAndShields()
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
        guard morningGoalCompletionAllowed else { return }
        tickerTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                await MainActor.run {
                    guard morningGoalCompletionAllowed else {
                        stopTicker()
                        return
                    }
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
