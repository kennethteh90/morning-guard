import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState
    @State private var showGoal = false
    @State private var showSettings = false
    @State private var showEmergency = false
    @State private var showBibleReader = false
    @State private var showHighlights = false
    @State private var showStats = false

    var body: some View {
        Group {
            if appState.settingsStore.userSettings.onboardingCompleted {
                NavigationStack {
                    HomeView(
                        onOpenGoal: { showGoal = true },
                        onOpenBibleReader: { showBibleReader = true },
                        onOpenHighlights: { showHighlights = true },
                        onOpenStats: { showStats = true },
                        onOpenSettings: { showSettings = true },
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
                    .navigationDestination(isPresented: $showEmergency) {
                        EmergencyBypassView()
                    }
                }
            } else {
                OnboardingFlowView()
            }
        }
        .onChange(of: appState.deepLinkGoalPending) { _, pending in
            if pending, appState.settingsStore.userSettings.onboardingCompleted {
                showGoal = true
                appState.deepLinkGoalPending = false
            }
        }
        .onChange(of: appState.settingsStore.userSettings.onboardingCompleted) { wasComplete, isComplete in
            if isComplete, !wasComplete {
                showGoal = true
            }
        }
    }
}
