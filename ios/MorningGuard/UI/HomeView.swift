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
        List {
            if MGDebugFlags.showDebugPanels {
                Section {
                    debugBuildBanner
                }
            }

            Section {
                gateStatusRow
                shieldStatusRows
            }

            primarySection

            if peaceActive, let until = appState.peaceSessionService.activeUntilEpochMs {
                peaceActiveSection(until: until)
            } else if PeaceSessionPolicy.canOfferPeaceBreak(evaluation: evaluation, peaceSessionActive: peaceActive) {
                Section {
                    listNavButton(title: "Start peace break (1 hour)", systemImage: "timer") {
                        appState.startOrExtendPeaceSession()
                    }
                }
            }

            Section {
                listNavButton(title: "Read Bible", systemImage: "book.fill", action: onOpenBibleReader)
                listNavButton(title: "Streak & stats", systemImage: "chart.bar", action: onOpenStats)
                listNavButton(title: "Saved highlights", systemImage: "bookmark", action: onOpenHighlights)
            }

            if evaluation.state == .locked, evaluation.reason == "morning_window" {
                Section {
                    Button("Emergency bypass", action: onOpenEmergency)
                        .buttonStyle(.plain)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
        }
        .listStyle(.insetGrouped)
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

    private func listNavButton(title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            MGListNavigationRow(title: title, systemImage: systemImage)
        }
        .buttonStyle(.plain)
    }

    private var gateStatusRow: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(gateTitle, systemImage: gateIcon)
                .font(.title3.weight(.semibold))
            Text(gateSubtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var shieldStatusRows: some View {
        if appState.shieldController.isAuthorized {
            LabeledContent("Apps shielded") {
                Text("\(appState.shieldController.shieldedAppCount)")
            }
            LabeledContent("Status") {
                Text(appState.shieldController.shieldsApplied ? "Active" : "Off")
                    .foregroundStyle(appState.shieldController.shieldsApplied ? .orange : .secondary)
            }
        } else if MGDebugFlags.relaxesShieldOnboarding {
            Text("Screen Time not authorized (OK in Simulator). Shields apply on a real device after authorization.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        } else {
            Text("Screen Time not authorized")
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var primarySection: some View {
        if peaceActive {
            EmptyView()
        } else if evaluation.state == .locked, evaluation.reason == "morning_window" {
            Section {
                Button(action: onOpenGoal) {
                    MGListPrimaryActionLabel(title: "Start morning goal")
                }
                .buttonStyle(.plain)
                .listRowBackground(MGTheme.accent)
            }
        } else if evaluation.state == .inactive {
            Section {
                Text("Outside your morning window. The gate activates during the hours you chose in setup.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        } else if evaluation.state == .unlocked, evaluation.reason == "goal_complete" {
            Section {
                Text("Today's morning goal is complete.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func peaceActiveSection(until: Int64) -> some View {
        let remainingMin = max(1, Int((until - nowMs()) / 60_000))
        return Section {
            VStack(alignment: .leading, spacing: 10) {
                Label("Peace break", systemImage: "leaf.fill")
                    .font(.headline)
                    .foregroundStyle(MGTheme.accent)
                Text("About \(remainingMin) min left. Selected apps stay shielded.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                HStack {
                    Button("End peace break") {
                        appState.endPeaceSession()
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
                    Spacer()
                    Button("Extend 1 hour") {
                        appState.startOrExtendPeaceSession()
                    }
                    .buttonStyle(.plain)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(MGTheme.accent)
                }
            }
            .padding(.vertical, 4)
        }
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
        .listRowBackground(MGTheme.accent.opacity(0.12))
    }
}
