import MorningGuardCore
import SwiftUI

struct HomeView: View {
    @Environment(AppState.self) private var appState

    let onOpenGoal: () -> Void
    let onOpenSettings: () -> Void
    let onOpenEmergency: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                gateCard
                shieldCard
                actions
            }
            .padding(MGTheme.standardPadding)
        }
        .background(MGTheme.background)
        .navigationTitle("Morning Guard")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel("Settings")
            }
        }
    }

    private var gateCard: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 8) {
                Label(gateTitle, systemImage: gateIcon)
                    .font(MGTheme.titleFont)
                Text(gateSubtitle)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var shieldCard: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Shields")
                    .font(MGTheme.titleFont)
                if appState.shieldController.isAuthorized {
                    Text("\(appState.shieldController.shieldedAppCount) apps selected")
                    Text(appState.shieldController.shieldsApplied ? "Shields active" : "Shields off")
                        .foregroundStyle(appState.shieldController.shieldsApplied ? .orange : .secondary)
                } else {
                    Text("Screen Time not authorized")
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var actions: some View {
        VStack(spacing: 12) {
            if appState.gateService.evaluation.state == .locked {
                Button("Start morning goal", action: onOpenGoal)
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
            }
            Button("Emergency bypass", action: onOpenEmergency)
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
        }
    }

    private var gateTitle: String {
        switch appState.gateService.evaluation.state {
        case .locked: "Morning gate locked"
        case .unlocked: "Unlocked"
        case .inactive: "Outside morning window"
        }
    }

    private var gateIcon: String {
        switch appState.gateService.evaluation.state {
        case .locked: "lock.fill"
        case .unlocked: "lock.open.fill"
        case .inactive: "moon.zzz.fill"
        }
    }

    private var gateSubtitle: String {
        switch appState.gateService.evaluation.reason {
        case "morning_window":
            return "Complete your goal to unlock selected apps."
        case "goal_complete":
            return "Today's goal is complete."
        case "emergency_bypass":
            return "Emergency bypass is active for one hour."
        default:
            return "Window: \(windowDescription)"
        }
    }

    private var windowDescription: String {
        let w = appState.settingsStore.userSettings.morningWindow
        return String(format: "%02d:%02d – %02d:%02d", w.startHour, w.startMinute, w.endHour, w.endMinute)
    }
}
