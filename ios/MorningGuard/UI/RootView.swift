import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState
    @State private var showGoal = false
    @State private var showSettings = false
    @State private var showEmergency = false
    @State private var showSuccess = false

    var body: some View {
        Group {
            if appState.settingsStore.userSettings.onboardingCompleted {
                NavigationStack {
                    HomeView(
                        onOpenGoal: { showGoal = true },
                        onOpenSettings: { showSettings = true },
                        onOpenEmergency: { showEmergency = true }
                    )
                    .navigationDestination(isPresented: $showGoal) {
                        MorningGoalView(onCompleted: {
                            showGoal = false
                            showSuccess = true
                        })
                    }
                    .navigationDestination(isPresented: $showSettings) {
                        SettingsView()
                    }
                    .navigationDestination(isPresented: $showEmergency) {
                        EmergencyBypassView()
                    }
                    .navigationDestination(isPresented: $showSuccess) {
                        GoalSuccessView(onDone: { showSuccess = false })
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
    }
}
