import SwiftUI

@main
struct MorningGuardApp: App {
    @State private var appState = AppState()

    init() {
        SentryBootstrap.startIfConfigured()
    }

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appState)
                .tint(MGTheme.accent)
                .onAppear {
                    appState.onAppear()
                    appState.startPeriodicGateRefresh()
                }
                .onOpenURL { url in
                    appState.handleDeepLink(url)
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                appState.onSceneActive()
            }
        }
    }
}
