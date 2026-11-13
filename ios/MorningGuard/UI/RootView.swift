import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState
    @State private var showGoal = false
    @State private var showSettings = false
    @State private var showEmergency = false
    @State private var showBibleReader = false
    @State private var showHighlights = false
    @State private var showStats = false
    @State private var showAccount = false

    var body: some View {
        RecoveryPasswordHostView {
        Group {
            if appState.settingsStore.userSettings.onboardingCompleted {
                NavigationStack {
                    HomeView(
                        onOpenGoal: { showGoal = true },
                        onOpenBibleReader: { showBibleReader = true },
                        onOpenHighlights: { showHighlights = true },
                        onOpenStats: { showStats = true },
                        onOpenSettings: { showSettings = true },
                        onOpenAccount: { showAccount = true },
                        onOpenEmergency: { showEmergency = true }
                    )
                    .navigationDestination(isPresented: $showGoal) {
                        MorningGoalView(onCompleted: { showGoal = false })
                    }
                    .navigationDestination(isPresented: $showBibleReader) {
                        BibleReaderView()
                    }
                    .navigationDestination(isPresented: $showHighlights) {
                        HighlightsView()
                    }
                    .navigationDestination(isPresented: $showStats) {
                        StatsView()
                    }
                    .navigationDestination(isPresented: $showSettings) {
                        SettingsView()
                    }
                    .navigationDestination(isPresented: $showAccount) {
                        AccountView()
                    }
                    .navigationDestination(isPresented: $showEmergency) {
                        EmergencyBypassView()
                    }
                }
            } else {
                OnboardingFlowView()
            }
        }
        }
        .onChange(of: appState.deepLinkGoalPending) { _, pending in
            presentGoalIfEligible(pending: pending)
        }
        .onChange(of: appState.settingsStore.userSettings.onboardingCompleted) { _, completed in
            if completed {
                presentGoalIfEligible(pending: appState.deepLinkGoalPending)
            }
        }
        .onAppear {
            presentGoalIfEligible(pending: appState.deepLinkGoalPending)
        }
        .task {
            await applyScreenshotGuideSceneIfNeeded()
        }
    }

    private func presentGoalIfEligible(pending: Bool) {
        guard pending, appState.settingsStore.userSettings.onboardingCompleted else { return }
        showGoal = true
        appState.deepLinkGoalPending = false
    }

    private func applyScreenshotGuideSceneIfNeeded() async {
        guard let scene = appState.screenshotGuideScene else { return }
        try? await Task.sleep(for: .milliseconds(800))
        switch scene {
        case "goal":
            showGoal = true
        case "settings":
            showSettings = true
        default:
            break
        }
    }
}
