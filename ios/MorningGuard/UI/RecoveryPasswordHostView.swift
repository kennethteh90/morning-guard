import SwiftUI

struct RecoveryPasswordHostView<Content: View>: View {
    @Environment(AppState.self) private var appState
    @ViewBuilder var content: () -> Content

    @State private var password = ""
    @State private var confirmation = ""
    @State private var showStartFreshConfirm = false

    var body: some View {
        content()
            .confirmationDialog(
                L10n.tr("recovery.start_fresh.title"),
                isPresented: $showStartFreshConfirm,
                titleVisibility: .visible
            ) {
                Button(L10n.tr("recovery.start_fresh.button"), role: .destructive) {
                    appState.recoveryPassword.startFreshWithoutCloudNotes()
                }
                .accessibilityLabel(L10n.tr("a11y.recovery.start_fresh"))
                .accessibilityHint(L10n.tr("a11y.recovery.start_fresh.hint"))
                Button(L10n.tr("common.cancel"), role: .cancel) {}
            } message: {
                Text(L10n.tr("recovery.start_fresh.message"))
            }
            .sheet(isPresented: blockingBinding) {
                blockingRecoverySheet
            }
            .sheet(isPresented: dedicatedBinding) {
                dedicatedRecoverySheet
            }
    }

    private var blockingBinding: Binding<Bool> {
        Binding(
            get: { appState.recoveryPassword.showBlockingCloudRecovery },
            set: { _ in }
        )
    }

    private var dedicatedBinding: Binding<Bool> {
        Binding(
            get: {
                !appState.recoveryPassword.showBlockingCloudRecovery
                    && appState.recoveryPassword.showDedicatedSheet
            },
            set: { newValue in
                if !newValue { appState.recoveryPassword.dismissLater() }
            }
        )
    }

    private var blockingRecoverySheet: some View {
        NavigationStack {
            Form {
                Section {
                    MGSettingsSectionIntro(text: L10n.tr("recovery.blocking.intro"))
                    SecureField(L10n.tr("settings.notes.recovery_field"), text: $password)
                        .accessibilityLabel(L10n.tr("a11y.recovery.password"))
                    if let error = appState.recoveryPassword.errorMessage {
                        MGSettingsInlineMessage(text: error, style: .error)
                    }
                    Button(L10n.tr("recovery.blocking.forgot")) {
                        showStartFreshConfirm = true
                    }
                    .font(.subheadline)
                    .accessibilityLabel(L10n.tr("a11y.recovery.forgot"))
                }
                Section {
                    MGSettingsPrimaryButton(title: L10n.tr("recovery.blocking.unlock"), disabled: password.count < 8) {
                        appState.recoveryPassword.unlockCloudRecovery(password: password)
                        password = ""
                    }
                    .accessibilityLabel(L10n.tr("a11y.recovery.unlock"))
                }
            }
            .navigationTitle(L10n.tr("recovery.blocking.title"))
            .interactiveDismissDisabled()
        }
    }

    private var dedicatedRecoverySheet: some View {
        NavigationStack {
            Form {
                Section {
                    Text(L10n.tr("recovery.dedicated.title"))
                        .font(.title3.weight(.semibold))
                    MGSettingsSectionIntro(text: L10n.tr("recovery.dedicated.body"))
                    MGSettingsSectionIntro(text: L10n.tr("settings.notes.rules"))
                    SecureField(L10n.tr("settings.notes.recovery_field"), text: $password)
                        .accessibilityLabel(L10n.tr("a11y.recovery.password"))
                    SecureField(L10n.tr("settings.notes.confirm_field"), text: $confirmation)
                    if let error = appState.recoveryPassword.errorMessage {
                        MGSettingsInlineMessage(text: error, style: .error)
                    }
                    if let status = appState.recoveryPassword.statusMessage {
                        MGSettingsInlineMessage(text: status, style: .success)
                    }
                }
                Section {
                    MGSettingsPrimaryButton(
                        title: L10n.tr("settings.notes.set_button"),
                        disabled: password.count < 8 || confirmation.count < 8
                    ) {
                        appState.recoveryPassword.setRecoveryPassword(password: password, confirmation: confirmation)
                        password = ""
                        confirmation = ""
                        appState.recoveryPassword.markPromptShown()
                    }
                    .accessibilityLabel(L10n.tr("a11y.recovery.set"))
                    Button(L10n.tr("recovery.dedicated.later")) {
                        appState.recoveryPassword.dismissLater()
                        appState.recoveryPassword.markPromptShown()
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
            }
            .navigationTitle(L10n.tr("recovery.dedicated.title"))
        }
    }
}

struct RecoveryPasswordBannerView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        if appState.recoveryPassword.showBanner, !appState.noteEncryption.cryptoState.hasRecoveryPassword() {
            VStack(alignment: .leading, spacing: 10) {
                Text(L10n.tr("recovery.banner.not_set.title"))
                    .font(.subheadline.weight(.semibold))
                Text(L10n.tr("recovery.banner.not_set.body"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Button(L10n.tr("recovery.banner.set_password")) {
                    appState.recoveryPassword.onSignedIn()
                }
                .buttonStyle(.borderedProminent)
                .tint(MGTheme.accent)
                .accessibilityLabel(L10n.tr("a11y.recovery.banner.set"))
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(MGTheme.accent.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .accessibilityElement(children: .contain)
        }
    }
}
