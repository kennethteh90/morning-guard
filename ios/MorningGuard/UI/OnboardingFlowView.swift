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

    #if canImport(FamilyControls)
    @State private var familyActivitySelection = FamilyActivitySelection()
    #endif

    private var stepTitles: [String] {
        [
            L10n.tr("onboarding.step.welcome"),
            L10n.tr("onboarding.step.your_data"),
            L10n.tr("onboarding.step.access"),
            L10n.tr("onboarding.step.apps"),
            L10n.tr("onboarding.step.routine"),
            L10n.tr("onboarding.step.emergency"),
        ]
    }

    private var signedInUser: AuthUser? {
        if case .signedIn(let user) = appState.supabaseAuth.state { return user }
        return nil
    }

    private var isSignedInForOnboarding: Bool { signedInUser != nil }

    var body: some View {
        VStack(spacing: 0) {
            OnboardingStepHeader(steps: stepTitles, currentIndex: step)
                .padding(.horizontal, MGTheme.standardPadding)
                .padding(.top, 12)

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
            #if canImport(FamilyControls)
            syncFamilyActivitySelectionFromController()
            #endif
        }
        .onChange(of: step) { _, newStep in
            if newStep == 3 {
                appState.shieldController.refreshAuthorizationStatus()
                #if canImport(FamilyControls)
                syncFamilyActivitySelectionFromController()
                #endif
            }
        }
    }

    private var welcomeStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                OnboardingSectionTitle(
                    title: L10n.tr("onboarding.welcome.title"),
                    subtitle: L10n.tr("onboarding.welcome.subtitle")
                )

                featureRow(
                    icon: "sun.horizon.fill",
                    title: L10n.tr("onboarding.welcome.gate"),
                    detail: L10n.tr("onboarding.welcome.gate.detail")
                )
                featureRow(
                    icon: "bell.slash.fill",
                    title: L10n.tr("onboarding.welcome.no_digest"),
                    detail: L10n.tr("onboarding.welcome.no_digest.detail")
                )
                featureRow(
                    icon: "shield.lefthalf.filled",
                    title: L10n.tr("onboarding.welcome.control"),
                    detail: L10n.tr("onboarding.welcome.control.detail")
                )
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var yourDataStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OnboardingSectionTitle(
                    title: L10n.tr("onboarding.data.title"),
                    subtitle: isSignedInForOnboarding
                        ? L10n.tr("onboarding.data.subtitle_signed_in")
                        : L10n.tr("onboarding.data.subtitle")
                )

                if isSignedInForOnboarding {
                    signedInDataCards
                } else {
                    unsignedInDataFlow
                }
            }
            .padding(MGTheme.standardPadding)
        }
    }

    @ViewBuilder
    private var signedInDataCards: some View {
        dataChoiceCard(
            title: L10n.tr("onboarding.data.local.title"),
            detail: L10n.tr("onboarding.data.local.detail_signed_in"),
            icon: "icloud.slash",
            selected: choseLocalOnly,
            accessibilityLabel: L10n.tr("a11y.onboarding.data.local_signed_in"),
            accessibilityHint: L10n.tr("a11y.onboarding.data.local_signed_in.hint"),
            action: { Task { await continueWithLocalData() } }
        )

        dataChoiceCard(
            title: L10n.tr("onboarding.data.cloud.title"),
            detail: cloudDetailSignedIn,
            icon: "envelope.badge",
            selected: !choseLocalOnly,
            accessibilityLabel: L10n.tr("a11y.onboarding.data.cloud_signed_in"),
            accessibilityHint: L10n.tr("a11y.onboarding.data.cloud_signed_in.hint"),
            action: { continueWithCloudSync() }
        )
    }

    @ViewBuilder
    private var unsignedInDataFlow: some View {
        dataChoiceCard(
            title: L10n.tr("onboarding.data.local.title"),
            detail: L10n.tr("onboarding.data.local.detail"),
            icon: "icloud.slash",
            selected: choseLocalOnly,
            accessibilityLabel: L10n.tr("a11y.onboarding.data.local"),
            accessibilityHint: L10n.tr("a11y.onboarding.data.local.hint"),
            action: { choseLocalOnly = true }
        )

        Text(L10n.tr("onboarding.data.or_sync"))
            .font(.headline)
            .accessibilityAddTraits(.isHeader)

        TextField(L10n.tr("onboarding.data.email"), text: $syncEmail)
            .textContentType(.emailAddress)
            .keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .accessibilityLabel(L10n.tr("a11y.onboarding.email"))

        Text(L10n.tr("onboarding.data.email.hint"))
            .font(.caption)
            .foregroundStyle(.secondary)

        if let magicLinkError {
            Text(magicLinkError).foregroundStyle(.red).font(.callout)
        }
        if let magicLinkStatus {
            Text(magicLinkStatus).foregroundStyle(MGTheme.accent).font(.callout)
        }
    }

    private var cloudDetailSignedIn: String {
        let emailSuffix: String
        if let email = signedInUser?.email, !email.isEmpty {
            emailSuffix = L10n.tr("onboarding.data.cloud.detail_email_format", email)
        } else {
            emailSuffix = ""
        }
        return L10n.tr("onboarding.data.cloud.detail_format", emailSuffix)
    }

    private var screenTimeStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                OnboardingSectionTitle(
                    title: L10n.tr("onboarding.screentime.title"),
                    subtitle: L10n.tr("onboarding.screentime.subtitle")
                )

                if MGDebugFlags.relaxesShieldOnboarding {
                    OnboardingSimulatorBanner(text: L10n.tr("onboarding.screentime.simulator"))
                }

                MGCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Label(L10n.tr("onboarding.screentime.individual"), systemImage: "person.crop.circle")
                            .font(.headline)
                        Text(L10n.tr("onboarding.screentime.self_control"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)

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
                                appState.shieldController.isAuthorized
                                    ? L10n.tr("onboarding.screentime.authorized")
                                    : L10n.tr("onboarding.screentime.authorize"),
                                systemImage: appState.shieldController.isAuthorized ? "checkmark.circle.fill" : "hourglass.circle"
                            )
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(MGTheme.accent)
                    .disabled(appState.shieldController.isAuthorized || isRequestingAuth)
                    .accessibilityLabel(L10n.tr("a11y.onboarding.authorize_screentime"))
                    .accessibilityHint(L10n.tr("a11y.onboarding.authorize_screentime.hint"))
                }
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var appPickerStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OnboardingSectionTitle(
                    title: L10n.tr("onboarding.apps.title"),
                    subtitle: L10n.tr("onboarding.apps.subtitle")
                )

                if MGDebugFlags.relaxesShieldOnboarding {
                    OnboardingSimulatorBanner(text: L10n.tr("onboarding.apps.simulator"))
                }

                #if canImport(FamilyControls)
                if appState.shieldController.isAuthorized || MGDebugFlags.relaxesShieldOnboarding {
                    appPickerCard
                } else {
                    MGCard {
                        Text(L10n.tr("onboarding.apps.need_auth"))
                            .foregroundStyle(.secondary)
                    }
                }
                #else
                MGCard {
                    Text(L10n.tr("onboarding.apps.unavailable"))
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
                    title: L10n.tr("onboarding.routine.title"),
                    subtitle: L10n.tr("onboarding.routine.subtitle")
                )

                MGCard {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(L10n.tr("onboarding.routine.window"))
                            .font(.headline)
                        DatePicker(L10n.tr("settings.starts"), selection: $windowStart, displayedComponents: .hourAndMinute)
                        DatePicker(L10n.tr("settings.ends"), selection: $windowEnd, displayedComponents: .hourAndMinute)
                    }
                }
                .accessibilityElement(children: .contain)

                MGCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(L10n.tr("onboarding.routine.daily_goal"))
                            .font(.headline)
                        Picker(L10n.tr("settings.goal.type"), selection: $selectedGoal) {
                            Text(L10n.tr("onboarding.routine.goal.bible")).tag(GoalType.bibleChapter)
                            Text(L10n.tr("onboarding.routine.goal.timed")).tag(GoalType.timedFocus)
                            Text(L10n.tr("onboarding.routine.goal.steps")).tag(GoalType.stepCount)
                        }
                        .pickerStyle(.segmented)
                        Text(selectedGoalHint)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .contain)

                focusRecommendationCard

                tipForTestingWindow
            }
            .padding(MGTheme.standardPadding)
        }
    }

    private var focusRecommendationCard: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 8) {
                Label(L10n.tr("onboarding.focus.title"), systemImage: "moon.fill")
                    .font(.headline)
                Text(L10n.tr("onboarding.focus.body"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var emergencyStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OnboardingSectionTitle(
                    title: L10n.tr("onboarding.emergency.title"),
                    subtitle: L10n.tr("onboarding.emergency.subtitle")
                )
                SecureField(L10n.tr("onboarding.emergency.passphrase"), text: $emergencyPassphrase)
                    .accessibilityLabel(L10n.tr("a11y.onboarding.emergency_passphrase"))
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
            OnboardingSimulatorBanner(text: L10n.tr("onboarding.routine.simulator_tip"))
        }
    }

    private var selectedGoalHint: String {
        switch selectedGoal {
        case .bibleChapter:
            return L10n.tr("onboarding.routine.hint.bible")
        case .timedFocus:
            return L10n.tr("onboarding.routine.hint.timed")
        case .stepCount:
            return L10n.tr("onboarding.routine.hint.steps")
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 16) {
            if step > 0 {
                Button(L10n.tr("common.back")) { step -= 1 }
                    .buttonStyle(.bordered)
                    .accessibilityLabel(L10n.tr("common.back"))
                    .accessibilityHint(L10n.tr("a11y.onboarding.back"))
            }
            Spacer()
            if step != 1 || !isSignedInForOnboarding {
                Button(step == stepTitles.count - 1 ? L10n.tr("common.finish") : L10n.tr("common.continue")) {
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
                .accessibilityLabel(
                    step == stepTitles.count - 1 ? L10n.tr("a11y.onboarding.finish") : L10n.tr("a11y.onboarding.continue")
                )
            }
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
    private var appPickerCard: some View {
        ShieldAppSelectionEditor(
            selection: $familyActivitySelection,
            style: .onboardingCard,
            onSelectionChanged: {
                appState.shieldController.selection = familyActivitySelection
                appState.shieldController.persistSelection()
                appState.shieldController.refreshShields()
            }
        )
    }

    private func syncFamilyActivitySelectionFromController() {
        #if canImport(FamilyControls)
        familyActivitySelection = appState.shieldController.selection
        #endif
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
        .accessibilityElement(children: .combine)
    }

    private func dataChoiceCard(
        title: String,
        detail: String,
        icon: String,
        selected: Bool,
        accessibilityLabel: String,
        accessibilityHint: String,
        action: @escaping () -> Void
    ) -> some View {
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
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint(accessibilityHint)
    }

    private func continueWithLocalData() async {
        if isSignedInForOnboarding {
            await appState.signOut()
        }
        appState.settingsStore.updateUserSettings { settings in
            var copy = settings
            copy.dataStorageMode = .localOnly
            return copy
        }
        choseLocalOnly = true
        magicLinkError = nil
        magicLinkStatus = nil
        step += 1
    }

    private func continueWithCloudSync() {
        appState.settingsStore.updateUserSettings { settings in
            var copy = settings
            copy.dataStorageMode = .cloudSync
            return copy
        }
        choseLocalOnly = false
        magicLinkError = nil
        step += 1
    }

    private func applyDataChoiceAndAdvance() async {
        magicLinkError = nil
        magicLinkStatus = nil
        let trimmedEmail = syncEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        if choseLocalOnly && trimmedEmail.isEmpty {
            await continueWithLocalData()
            return
        }
        if !trimmedEmail.isEmpty {
            isSendingMagicLink = true
            defer { isSendingMagicLink = false }
            do {
                try await appState.sendMagicLink(email: trimmedEmail)
                choseLocalOnly = false
                magicLinkStatus = L10n.tr("onboarding.magic_link.sent")
            } catch {
                magicLinkError = error.localizedDescription
                return
            }
        } else if choseLocalOnly {
            await continueWithLocalData()
            return
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
            appState.shieldController.refreshAuthorizationStatus()
        } catch {
            authError = error.localizedDescription
        }
    }
}
