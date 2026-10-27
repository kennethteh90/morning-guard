import MorningGuardCore
import SwiftUI

struct HomeView: View {
    @Environment(AppState.self) private var appState

    let onOpenGoal: () -> Void
    let onOpenBibleReader: () -> Void
    let onOpenHighlights: () -> Void
    let onOpenStats: () -> Void
    let onOpenSettings: () -> Void
    let onOpenAccount: () -> Void
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
            }

            primarySection

            if peaceActive, let until = appState.peaceSessionService.activeUntilEpochMs {
                peaceActiveSection(until: until)
            } else if PeaceSessionPolicy.canOfferPeaceBreak(evaluation: evaluation, peaceSessionActive: peaceActive) {
                Section {
                    listNavButton(
                        title: L10n.tr("home.peace_break.start"),
                        systemImage: "timer",
                        accessibilityLabel: L10n.tr("a11y.home.peace_break"),
                        accessibilityHint: L10n.tr("a11y.home.peace_break.hint"),
                        action: { appState.startOrExtendPeaceSession() }
                    )
                }
            }

            Section {
                listNavButton(
                    title: L10n.tr("home.read_bible"),
                    systemImage: "book.fill",
                    accessibilityLabel: L10n.tr("a11y.home.read_bible"),
                    accessibilityHint: L10n.tr("a11y.home.read_bible.hint"),
                    action: onOpenBibleReader
                )
                listNavButton(
                    title: L10n.tr("home.streak_stats"),
                    systemImage: "chart.bar",
                    accessibilityLabel: L10n.tr("a11y.home.stats"),
                    accessibilityHint: L10n.tr("a11y.home.stats.hint"),
                    action: onOpenStats
                )
                listNavButton(
                    title: L10n.tr("home.highlights"),
                    systemImage: "bookmark",
                    accessibilityLabel: L10n.tr("a11y.home.highlights"),
                    accessibilityHint: L10n.tr("a11y.home.highlights.hint"),
                    action: onOpenHighlights
                )
            }

            if evaluation.state == .locked, evaluation.reason == "morning_window" {
                Section {
                    Button(L10n.tr("home.emergency_bypass"), action: onOpenEmergency)
                        .buttonStyle(.plain)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .accessibilityLabel(L10n.tr("a11y.home.emergency"))
                        .accessibilityHint(L10n.tr("a11y.home.emergency.hint"))
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L10n.tr("home.title"))
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onOpenAccount) {
                    Image(systemName: "person.crop.circle")
                }
                .accessibilityLabel(L10n.tr("a11y.home.account"))
                .accessibilityHint(L10n.tr("a11y.home.account.hint"))
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel(L10n.tr("a11y.home.settings"))
                .accessibilityHint(L10n.tr("a11y.home.settings.hint"))
            }
        }
        .onAppear {
            appState.refreshGateAndShields()
        }
    }

    private func listNavButton(
        title: String,
        systemImage: String,
        accessibilityLabel: String,
        accessibilityHint: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            MGListNavigationRow(title: title, systemImage: systemImage)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint(accessibilityHint)
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
        .accessibilityElement(children: .combine)
        .accessibilityLabel(L10n.tr("a11y.home.gate_status"))
        .accessibilityValue("\(gateTitle). \(gateSubtitle)")
        .accessibilityHint(L10n.tr("a11y.home.gate_status.hint"))
    }

    @ViewBuilder
    private var primarySection: some View {
        if peaceActive {
            EmptyView()
        } else if evaluation.state == .locked, evaluation.reason == "morning_window" {
            Section {
                Button(action: onOpenGoal) {
                    MGListPrimaryActionLabel(title: L10n.tr("home.start_goal"))
                }
                .buttonStyle(.plain)
                .listRowBackground(MGTheme.accent)
                .accessibilityLabel(L10n.tr("a11y.home.start_goal"))
                .accessibilityHint(L10n.tr("home.start_goal.hint"))
            }
        } else if evaluation.state == .inactive {
            Section {
                Text(L10n.tr("home.outside_window"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel(L10n.tr("home.outside_window"))
            }
        } else if evaluation.state == .unlocked, evaluation.reason == "goal_complete" {
            Section {
                Text(L10n.tr("home.goal_complete"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel(L10n.tr("home.goal_complete"))
            }
        }
    }

    private func peaceActiveSection(until: Int64) -> some View {
        let remainingMin = max(1, Int((until - nowMs()) / 60_000))
        return Section {
            VStack(alignment: .leading, spacing: 10) {
                Label(L10n.tr("home.peace_break.title"), systemImage: "leaf.fill")
                    .font(.headline)
                    .foregroundStyle(MGTheme.accent)
                Text(L10n.tr("home.peace_break.remaining_format", remainingMin))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                HStack {
                    Button(L10n.tr("home.peace_break.end")) {
                        appState.endPeaceSession()
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel(L10n.tr("a11y.home.end_peace_break"))
                    .accessibilityHint(L10n.tr("a11y.home.end_peace_break.hint"))
                    Spacer()
                    Button(L10n.tr("home.peace_break.extend")) {
                        appState.startOrExtendPeaceSession()
                    }
                    .buttonStyle(.plain)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(MGTheme.accent)
                    .accessibilityLabel(L10n.tr("a11y.home.extend_peace_break"))
                    .accessibilityHint(L10n.tr("a11y.home.extend_peace_break.hint"))
                }
            }
            .padding(.vertical, 4)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(L10n.tr("a11y.home.peace_section"))
        }
    }

    private var gateTitle: String {
        switch evaluation.reason {
        case "peace_session": return L10n.tr("home.gate.peace_session")
        case "morning_window": return L10n.tr("home.gate.morning_locked")
        case "goal_complete": return L10n.tr("home.gate.unlocked")
        case "emergency_bypass": return L10n.tr("home.gate.unlocked")
        default:
            switch evaluation.state {
            case .locked: return L10n.tr("home.gate.morning_locked")
            case .unlocked: return L10n.tr("home.gate.unlocked")
            case .inactive: return L10n.tr("home.gate.outside_window")
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
            return L10n.tr("home.subtitle.peace_session")
        case "morning_window":
            return L10n.tr("home.subtitle.morning_window")
        case "goal_complete":
            return L10n.tr("home.subtitle.goal_complete")
        case "emergency_bypass":
            return L10n.tr("home.subtitle.emergency_bypass")
        default:
            return L10n.tr("home.subtitle.window_format", windowDescription)
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
            Text(L10n.tr("home.debug.banner"))
                .font(.caption)
        }
        .foregroundStyle(MGTheme.accent)
        .listRowBackground(MGTheme.accent.opacity(0.12))
        .accessibilityLabel(L10n.tr("home.debug.banner"))
    }
}
