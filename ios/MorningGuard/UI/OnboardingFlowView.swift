import MorningGuardCore
import SwiftUI

#if canImport(FamilyControls)
import FamilyControls
#endif

struct OnboardingFlowView: View {
    @Environment(AppState.self) private var appState
    @State private var step = 0
    @State private var authError: String?
    @State private var isRequestingAuth = false

    @State private var windowStart = Date()
    @State private var windowEnd = Date()
    @State private var selectedGoal: GoalType = .bibleChapter

    @State private var choseLocalOnly = true
    @State private var syncEmail = ""
    @State private var magicLinkStatus: String?
    @State private var magicLinkError: String?
    @State private var isSendingMagicLink = false

    @State private var emergencyPassphrase = ""
    @State private var emergencyPassphraseError: String?

    private let stepTitles = [
        String(localized: "onboarding.step.welcome", defaultValue: "Welcome"),
        String(localized: "onboarding.step.your_data", defaultValue: "Your data"),
        String(localized: "onboarding.step.access", defaultValue: "Access"),
        String(localized: "onboarding.step.apps", defaultValue: "Apps"),
        String(localized: "onboarding.step.routine", defaultValue: "Routine"),
        String(localized: "onboarding.step.emergency", defaultValue: "Emergency"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            OnboardingStepHeader(steps: stepTitles, currentIndex: step)
                .padding(.horizontal, MGTheme.standardPadding)
                .padding(.top, 12)
                .accessibilityLabel("Setup step \(step + 1) of \(stepTitles.count)")

            Group {
                switch step {
                case 0: welcomeStep
                case 1: yourDataStep
                case 2: screenTimeStep
                case 3: appPickerStep
                case 4: setupStep
                default: emergencyStep
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .animation(.easeInOut, value: step)
        }
        .background(MGTheme.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            bottomBar
        }
        .onAppear {
            syncWindowPickers()
            selectedGoal = appState.settingsStore.userSettings.morningGoal.type
            choseLocalOnly = appState.settingsStore.userSettings.dataStorageMode == .localOnly
        }
    }

    private var welcomeStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                OnboardingSectionTitle(
                    title: String(localized: "onboarding.welcome.title", defaultValue: "Welcome to Morning Guard"),
                    subtitle: String(
                        localized: "onboarding.welcome.subtitle",
                        defaultValue: "Start your day with intention. On iPhone we shield apps you pick—not other apps’ notifications."
                    )
                )

                featureRow(
                    icon: "sun.horizon.fill",
                    title: String(localized: "onboarding.welcome.gate", defaultValue: "Morning gate"),
                    detail: String(localized: "onboarding.welcome.gate.detail", defaultValue: "Protect your first hours with Screen Time shields.")
                )
                featureRow(
                    icon: "bell.slash.fill",
                    title: String(localized: "onboarding.welcome.no_digest", defaultValue: "No notification digest"),
                    detail: String(
                        localized: "onboarding.welcome.no_digest.detail",
                        defaultValue: "iOS cannot hold or summarize notifications from other apps. Use Focus in Settings if you need quiet alerts."
                    )
                )
                featureRow(
                    icon: "shield.lefthalf.filled",
                    title: String(localized: "onboarding.welcome.control", defaultValue: "You're in control"),
                    detail: String(localized: "onboarding.welcome.control.detail", defaultValue: "Peace break, emergency passphrase, and local encrypted backup.")
                )
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var yourDataStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OnboardingSectionTitle(
                    title: String(localized: "onboarding.data.title", defaultValue: "Your data"),
                    subtitle: String(
                        localized: "onboarding.data.subtitle",
                        defaultValue: "Sync streaks and highlights with a magic link, or keep everything on this iPhone only."
                    )
                )

                dataChoiceCard(
                    title: String(localized: "onboarding.data.local.title", defaultValue: "Keep on this device"),
                    detail: String(
                        localized: "onboarding.data.local.detail",
                        defaultValue: "No account. Export an encrypted backup from Settings anytime."
                    ),
                    icon: "icloud.slash",
                    selected: choseLocalOnly,
                    action: { choseLocalOnly = true }
                )

                Text(String(localized: "onboarding.data.or_sync", defaultValue: "Or sync with email"))
                    .font(.headline)

                TextField(
                    String(localized: "onboarding.data.email", defaultValue: "Email for magic link"),
                    text: $syncEmail
                )
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .accessibilityLabel("Email for magic link")

                Text(
                    String(
                        localized: "onboarding.data.email.hint",
                        defaultValue: "Finish setup now—open the link on this iPhone when it arrives. Delivery can take a few minutes."
                    )
                )
                .font(.caption)
                .foregroundStyle(.secondary)

                if let magicLinkError {
                    Text(magicLinkError).foregroundStyle(.red).font(.callout)
                }
                if let magicLinkStatus {
                    Text(magicLinkStatus).foregroundStyle(MGTheme.accent).font(.callout)
                }
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var screenTimeStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                OnboardingSectionTitle(
                    title: String(localized: "onboarding.screentime.title", defaultValue: "Screen Time access"),
                    subtitle: String(
                        localized: "onboarding.screentime.subtitle",
                        defaultValue: "Morning Guard uses Apple’s Screen Time API to shield apps you pick until you finish your morning goal."
                    )
                )

                if MGDebugFlags.relaxesShieldOnboarding {
                    OnboardingSimulatorBanner(
                        text: "Simulator: authorization isn’t required. Shields only work on a physical device."
                    )
                }

                MGCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("Individual authorization", systemImage: "person.crop.circle")
                            .font(.headline)
                        Text("Self-control on your device—not parental monitoring.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                if let authError {
                    Text(authError)
                        .foregroundStyle(.red)
                        .font(.callout)
                }

                if !MGDebugFlags.relaxesShieldOnboarding {
                    Button {
                        Task { await requestFamilyControls() }
                    } label: {
                        if isRequestingAuth {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Label(
                                appState.shieldController.isAuthorized ? "Authorized" : "Authorize Screen Time",
                                systemImage: appState.shieldController.isAuthorized ? "checkmark.circle.fill" : "hourglass.circle"
                            )
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(MGTheme.accent)
                    .disabled(appState.shieldController.isAuthorized || isRequestingAuth)
                    .accessibilityLabel("Authorize Screen Time")
                }
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var appPickerStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OnboardingSectionTitle(
                    title: String(localized: "onboarding.apps.title", defaultValue: "Apps to shield"),
                    subtitle: String(
                        localized: "onboarding.apps.subtitle",
                        defaultValue: "Choose what to limit during your morning window. Leave out Phone and Messages if you need them."
                    )
                )

                if MGDebugFlags.relaxesShieldOnboarding {
                    OnboardingSimulatorBanner(
                        text: "Simulator: the app picker may be empty. You can continue without selecting apps."
                    )
                }

                #if canImport(FamilyControls)
                if appState.shieldController.isAuthorized || MGDebugFlags.relaxesShieldOnboarding {
                    FamilyActivityPicker(selection: shieldSelectionBinding)
                        .frame(maxHeight: 260)
                        .accessibilityLabel("Apps to shield during morning window")
                } else {
                    MGCard {
                        Text("Authorize Screen Time on the previous step to choose apps.")
                            .foregroundStyle(.secondary)
                    }
                }
                #else
                MGCard {
                    Text("Family Controls unavailable in this build.")
                        .foregroundStyle(.secondary)
                }
                #endif
            }
            .padding(.horizontal, MGTheme.standardPadding)
            .padding(.bottom, MGTheme.standardPadding)
        }
    }

    private var setupStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                OnboardingSectionTitle(
                    title: String(localized: "onboarding.routine.title", defaultValue: "Your morning routine"),
                    subtitle: String(
                        localized: "onboarding.routine.subtitle",
                        defaultValue: "When this window is active and your goal isn’t done, selected apps stay shielded."
                    )
                )

                MGCard {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Morning window")
                            .font(.headline)
                        DatePicker("Starts", selection: $windowStart, displayedComponents: .hourAndMinute)
                        DatePicker("Ends", selection: $windowEnd, displayedComponents: .hourAndMinute)
                    }
                }

                MGCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Daily goal")
                            .font(.headline)
                        Picker("Type", selection: $selectedGoal) {
                            Text("Bible chapter (60s)").tag(GoalType.bibleChapter)
                            Text("Timed focus").tag(GoalType.timedFocus)
                            Text("Steps").tag(GoalType.stepCount)
                        }
                        .pickerStyle(.segmented)
                        Text(selectedGoalHint)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                tipForTestingWindow
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var emergencyStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OnboardingSectionTitle(
                    title: String(localized: "onboarding.emergency.title", defaultValue: "Emergency bypass"),
                    subtitle: String(
                        localized: "onboarding.emergency.subtitle",
                        defaultValue: "Optional. Set a passphrase now or later in Settings for a one-hour unlock during emergencies."
                    )
                )
                SecureField("Passphrase (8+ characters)", text: $emergencyPassphrase)
                    .accessibilityLabel("Emergency passphrase")
                if let emergencyPassphraseError {
                    Text(emergencyPassphraseError).foregroundStyle(.red).font(.callout)
                }
            }
            .padding(MGTheme.standardPadding)
        }
    }

    @ViewBuilder
    private var tipForTestingWindow: some View {
        if MGDebugFlags.relaxesShieldOnboarding {
            OnboardingSimulatorBanner(
                text: "Tip: set the window to include now, then finish onboarding and tap Start morning goal on Home."
            )
        }
    }

    private var selectedGoalHint: String {
        switch selectedGoal {
        case .bibleChapter:
            return "John 1 by default. Stay on the goal screen for about 60 seconds."
        case .timedFocus:
            return "Stay on the goal screen until the timer finishes (default 2 minutes)."
        case .stepCount:
            return "Counts steps via HealthKit; Simulator may not update steps."
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 16) {
            if step > 0 {
                Button("Back") { step -= 1 }
                    .buttonStyle(.bordered)
                    .accessibilityLabel("Back")
            }
            Spacer()
            Button(step == stepTitles.count - 1 ? "Finish" : "Continue") {
                if step == 1 {
                    Task { await applyDataChoiceAndAdvance() }
                } else if step == stepTitles.count - 1 {
                    finishOnboarding()
                } else {
                    step += 1
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(MGTheme.accent)
            .disabled(isContinueDisabled || (step == 1 && isSendingMagicLink))
            .accessibilityLabel(step == stepTitles.count - 1 ? "Finish setup" : "Continue")
        }
        .padding(MGTheme.standardPadding)
        .background(.bar)
        .shadow(color: .black.opacity(0.08), radius: 8, y: -2)
    }

    private var isContinueDisabled: Bool {
        if MGDebugFlags.relaxesShieldOnboarding { return false }
        switch step {
        case 2: return !appState.shieldController.isAuthorized
        case 3: return !appState.shieldController.hasSelection
        default: return false
        }
    }

    #if canImport(FamilyControls)
    private var shieldSelectionBinding: Binding<FamilyActivitySelection> {
        Binding(
            get: { appState.shieldController.selection },
            set: { newValue in
                appState.shieldController.selection = newValue
                appState.shieldController.persistSelection()
            }
        )
    }
    #endif

    private func featureRow(icon: String, title: String, detail: String) -> some View {
        MGCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon)
                    .foregroundStyle(MGTheme.accent)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline)
                    Text(detail).font(.subheadline).foregroundStyle(.secondary)
                }
            }
        }
    }

    private func dataChoiceCard(title: String, detail: String, icon: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            MGCard {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: icon)
                        .foregroundStyle(selected ? MGTheme.accent : .secondary)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title).font(.headline)
                        Text(detail).font(.subheadline).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if selected {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(MGTheme.accent)
                    }
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func applyDataChoiceAndAdvance() async {
        magicLinkError = nil
        magicLinkStatus = nil
        let trimmedEmail = syncEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        if choseLocalOnly && trimmedEmail.isEmpty {
            appState.settingsStore.updateUserSettings { settings in
                var copy = settings
                copy.dataStorageMode = .localOnly
                return copy
            }
            step += 1
            return
        }
        if !trimmedEmail.isEmpty {
            isSendingMagicLink = true
            defer { isSendingMagicLink = false }
            do {
                try await appState.sendMagicLink(email: trimmedEmail)
                choseLocalOnly = false
                magicLinkStatus = "Magic link sent. Open it on this iPhone when it arrives."
            } catch {
                magicLinkError = error.localizedDescription
                return
            }
        } else if choseLocalOnly {
            appState.settingsStore.updateUserSettings { settings in
                var copy = settings
                copy.dataStorageMode = .localOnly
                return copy
            }
        }
        step += 1
    }

    private func syncWindowPickers() {
        let window = appState.settingsStore.userSettings.morningWindow
        windowStart = date(hour: window.startHour, minute: window.startMinute)
        windowEnd = date(hour: window.endHour, minute: window.endMinute)
    }

    private func date(hour: Int, minute: Int) -> Date {
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour = hour
        components.minute = minute
        return Calendar.current.date(from: components) ?? Date()
    }

    private func finishOnboarding() {
        if !emergencyPassphrase.isEmpty {
            do {
                try appState.setEmergencyPassphrase(emergencyPassphrase)
            } catch {
                emergencyPassphraseError = error.localizedDescription
                return
            }
        }
        let window: MorningWindow
        if MGDebugFlags.relaxesShieldOnboarding {
            window = DebugWindowPresets.includingNow()
        } else {
            let start = Calendar.current.dateComponents([.hour, .minute], from: windowStart)
            let end = Calendar.current.dateComponents([.hour, .minute], from: windowEnd)
            window = MorningWindow(
                startHour: start.hour ?? 1,
                startMinute: start.minute ?? 0,
                endHour: end.hour ?? 10,
                endMinute: end.minute ?? 0
            )
        }
        appState.settingsStore.updateUserSettings { settings in
            var copy = settings
            copy.morningWindow = window
            copy.morningGoal = MorningGoalConfig(type: selectedGoal)
            if choseLocalOnly && syncEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                copy.dataStorageMode = .localOnly
            }
            return copy
        }
        appState.shieldController.persistSelection()
        appState.completeOnboarding()
    }

    private func requestFamilyControls() async {
        isRequestingAuth = true
        authError = nil
        defer { isRequestingAuth = false }
        do {
            try await appState.shieldController.requestAuthorization()
        } catch {
            authError = error.localizedDescription
        }
    }
}
