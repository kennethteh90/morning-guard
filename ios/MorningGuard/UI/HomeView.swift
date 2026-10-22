import MorningGuardCore
import SwiftUI

struct HomeView: View {
    @Environment(AppState.self) private var appState

    let onOpenGoal: () -> Void
    let onOpenBibleReader: () -> Void
    let onOpenHighlights: () -> Void
    let onOpenStats: () -> Void
    let onOpenSettings: () -> Void
    let onOpenEmergency: () -> Void

    private var evaluation: GateEvaluation {
        appState.gateService.evaluation
    }

    private var peaceActive: Bool {
        appState.peaceSessionService.activeUntilEpochMs != nil
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if MGDebugFlags.showDebugPanels {
                    debugBuildBanner
                }
                gateCard
                shieldCard
                primaryActions
                peaceSection
                readBibleButton
                homeNavCard
                iosNoteCard
                emergencyAction
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
        .onAppear {
            appState.refreshGateAndShields()
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
                } else if MGDebugFlags.relaxesShieldOnboarding {
                    Text("Screen Time not authorized (OK in Simulator). Shields apply on a real device after authorization.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    Text("Screen Time not authorized")
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var primaryActions: some View {
        if peaceActive {
            EmptyView()
        } else if evaluation.state == .locked, evaluation.reason == "morning_window" {
            Button("Start morning goal", action: onOpenGoal)
                .buttonStyle(.borderedProminent)
                .tint(MGTheme.accent)
                .frame(maxWidth: .infinity)
        } else if evaluation.state == .inactive {
            Text("Outside your morning window. The gate activates during the hours you chose in setup.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        } else if evaluation.state == .unlocked, goalCompleteToday {
            Text("Today's morning goal is complete.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var peaceSection: some View {
        if peaceActive, let until = appState.peaceSessionService.activeUntilEpochMs {
            let remainingMin = max(1, Int((until - nowMs()) / 60_000))
            MGCard {
                VStack(alignment: .leading, spacing: 12) {
                    Label("Peace break", systemImage: "leaf.fill")
                        .font(MGTheme.titleFont)
                        .foregroundStyle(MGTheme.accent)
                    Text("About \(remainingMin) min left. Selected apps stay shielded—iOS has no notification digest.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    HStack(spacing: 12) {
                        Button("End") {
                            appState.endPeaceSession()
                        }
                        .buttonStyle(.bordered)
                        Button("Extend 1 hour") {
                            appState.startOrExtendPeaceSession()
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(MGTheme.accent)
                    }
                }
            }
        } else if goalCompleteToday, !peaceActive {
            Button {
                appState.startOrExtendPeaceSession()
            } label: {
                Label("Start peace break (1 hour)", systemImage: "timer")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
    }

    private var readBibleButton: some View {
        Button(action: onOpenBibleReader) {
            Label("Read Bible", systemImage: "book.fill")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
    }

    private var homeNavCard: some View {
        MGCard {
            VStack(spacing: 0) {
                homeNavRow(label: "Streak & stats", systemImage: "chart.bar", action: onOpenStats)
                Divider()
                homeNavRow(label: "Saved highlights", systemImage: "bookmark", action: onOpenHighlights)
            }
        }
    }

    private func homeNavRow(label: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: systemImage)
                    .foregroundStyle(MGTheme.accent)
                Text(label)
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.vertical, 12)
        }
    }

    private var iosNoteCard: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 6) {
                Text("On iPhone")
                    .font(.headline)
                Text("Peace break re-enables app shields instead of holding notifications. Use Focus in Settings for notification quiet.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var emergencyAction: some View {
        Group {
            if evaluation.state == .locked, evaluation.reason == "morning_window" {
                Button("Emergency bypass", action: onOpenEmergency)
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var goalCompleteToday: Bool {
        appState.peaceSessionService.morningGoalCompleteToday
    }

    private var gateTitle: String {
        switch evaluation.reason {
        case "peace_session": return "Peace break"
        case "morning_window": return "Morning gate locked"
        case "goal_complete": return "Unlocked"
        case "emergency_bypass": return "Unlocked"
        default:
            switch evaluation.state {
            case .locked: return "Morning gate locked"
            case .unlocked: return "Unlocked"
            case .inactive: return "Outside morning window"
            }
        }
    }

    private var gateIcon: String {
        switch evaluation.reason {
        case "peace_session": return "leaf.fill"
        default:
            switch evaluation.state {
            case .locked: return "lock.fill"
            case .unlocked: return "lock.open.fill"
            case .inactive: return "moon.zzz.fill"
            }
        }
    }

    private var gateSubtitle: String {
        switch evaluation.reason {
        case "peace_session":
            return "Shields stay on for a quiet hour after your goal."
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

    private func nowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    private var debugBuildBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "ladybug.fill")
            Text("Debug build — open Settings for test window presets.")
                .font(.caption)
        }
        .foregroundStyle(MGTheme.accent)
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MGTheme.accent.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}
