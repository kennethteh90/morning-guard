import MorningGuardCore
import SwiftUI

/// Account sign-in and recovery password in one place — both are required for cloud note recovery.
struct SyncAndRecoverySettingsSection: View {
    @Environment(AppState.self) private var appState

    @State private var notesPassword = ""
    @State private var notesConfirmPassword = ""
    @State private var notesNewPassword = ""
    @State private var notesConfirmNewPassword = ""
    @State private var notesMessage: String?
    @State private var notesMessageStyle: MGSettingsInlineMessage.Style = .neutral

    private var usesCloudSync: Bool {
        appState.settingsStore.userSettings.dataStorageMode == .cloudSync
    }

    private var isSignedIn: Bool {
        appState.supabaseAuth.isSignedIn
    }

    private var hasRecoveryPassword: Bool {
        appState.noteEncryption.cryptoState.hasRecoveryPassword()
    }

    private var signedInEmail: String? {
        if case .signedIn(let user) = appState.supabaseAuth.state {
            return user.email
        }
        return nil
    }

    private var cloudRecoveryReady: Bool {
        usesCloudSync && isSignedIn && hasRecoveryPassword
    }

    var body: some View {
        Section {
            MGSettingsSectionIntro(
                text: usesCloudSync
                    ? L10n.tr("settings.recovery.intro.cloud")
                    : L10n.tr("settings.recovery.intro.local")
            )

            if usesCloudSync {
                cloudChecklist
            }

            if usesCloudSync {
                if cloudRecoveryReady {
                    MGSettingsStatusBanner(
                        title: L10n.tr("settings.recovery.ready"),
                        tone: .ready
                    )
                } else {
                    MGSettingsStatusBanner(
                        title: L10n.tr("settings.recovery.not_ready"),
                        tone: .actionNeeded
                    )
                }
            }

            recoveryPasswordFields
        } header: {
            Text(L10n.tr("settings.recovery.section"))
        } footer: {
            if !usesCloudSync {
                Text(L10n.tr("settings.recovery.footer.local_backup"))
            }
        }
    }

    @ViewBuilder
    private var cloudChecklist: some View {
        NavigationLink {
            AccountView()
        } label: {
            MGSettingsChecklistRow(
                step: 1,
                title: L10n.tr("settings.recovery.step.sign_in"),
                detail: signInStepDetail,
                isComplete: isSignedIn
            )
        }
        .accessibilityLabel(L10n.tr("a11y.settings.account_link"))
        .accessibilityHint(L10n.tr("settings.recovery.step.sign_in.hint"))

        MGSettingsChecklistRow(
            step: 2,
            title: L10n.tr("settings.recovery.step.password"),
            detail: passwordStepDetail,
            isComplete: hasRecoveryPassword
        )
        .accessibilityElement(children: .combine)
    }

    private var signInStepDetail: String {
        if let email = signedInEmail {
            return L10n.tr("settings.recovery.step.sign_in.detail.signed_in", email)
        }
        return L10n.tr("settings.recovery.step.sign_in.detail.needed")
    }

    private var passwordStepDetail: String {
        if hasRecoveryPassword {
            return L10n.tr("settings.recovery.step.password.detail.set")
        }
        return L10n.tr("settings.recovery.step.password.detail.needed")
    }

    @ViewBuilder
    private var recoveryPasswordFields: some View {
        if usesCloudSync, !isSignedIn {
            MGSettingsSectionIntro(text: L10n.tr("settings.recovery.password_after_sign_in"))
        } else {
            MGSettingsSubsectionTitle(text: L10n.tr("settings.recovery.form.header"))

            if !hasRecoveryPassword {
                MGSettingsSectionIntro(text: L10n.tr("settings.notes.rules"))
                SecureField(L10n.tr("settings.notes.recovery_field"), text: $notesPassword)
                    .textContentType(.password)
                    .accessibilityLabel(L10n.tr("a11y.recovery.password"))
                SecureField(L10n.tr("settings.notes.confirm_field"), text: $notesConfirmPassword)
                    .textContentType(.password)
                MGSettingsPrimaryButton(
                    title: L10n.tr("settings.notes.set_button"),
                    disabled: notesPassword.count < 8 || notesConfirmPassword.count < 8,
                    action: setupNotesEncryption
                )
                .accessibilityLabel(L10n.tr("a11y.settings.set_notes_password"))
            } else if !appState.noteEncryption.isUnlocked {
                MGSettingsSectionIntro(text: L10n.tr("settings.recovery.unlock_on_device"))
                SecureField(L10n.tr("settings.notes.recovery_field"), text: $notesPassword)
                    .textContentType(.password)
                    .accessibilityLabel(L10n.tr("a11y.recovery.password"))
                MGSettingsPrimaryButton(
                    title: L10n.tr("settings.notes.unlock_button"),
                    disabled: notesPassword.count < 8,
                    action: unlockNotesEncryption
                )
                .accessibilityLabel(L10n.tr("a11y.settings.unlock_notes"))
            } else {
                MGSettingsInlineMessage(text: L10n.tr("settings.notes.unlocked"), style: .success)
                SecureField(L10n.tr("settings.notes.current_field"), text: $notesPassword)
                    .textContentType(.password)
                SecureField(L10n.tr("settings.notes.new_field"), text: $notesNewPassword)
                    .textContentType(.password)
                SecureField(L10n.tr("settings.notes.confirm_new_field"), text: $notesConfirmNewPassword)
                    .textContentType(.password)
                MGSettingsSecondaryButton(
                    title: L10n.tr("settings.notes.change_button"),
                    disabled: notesPassword.count < 8
                        || notesNewPassword.count < 8
                        || notesConfirmNewPassword.count < 8,
                    action: changeNotesPassword
                )
                .accessibilityLabel(L10n.tr("a11y.settings.change_notes_password"))
            }

            if let notesMessage {
                MGSettingsInlineMessage(text: notesMessage, style: notesMessageStyle)
            }
        }
    }

    private func setupNotesEncryption() {
        if let validation = RecoveryPasswordRules.validate(password: notesPassword) {
            notesMessage = validation
            notesMessageStyle = .error
            return
        }
        if notesPassword != notesConfirmPassword {
            notesMessage = L10n.tr("settings.notes.passwords_mismatch")
            notesMessageStyle = .error
            return
        }
        do {
            try appState.noteEncryption.setupWithPassword(notesPassword)
            appState.bibleHighlightStore.reencryptAllStoredNotes()
            notesPassword = ""
            notesConfirmPassword = ""
            notesMessage = L10n.tr("settings.notes.saved")
            notesMessageStyle = .success
            Task { await appState.userDataSync.syncAllIfSignedIn() }
        } catch {
            notesMessage = error.localizedDescription
            notesMessageStyle = .error
        }
    }

    private func unlockNotesEncryption() {
        do {
            try appState.noteEncryption.unlockWithPassword(notesPassword)
            notesPassword = ""
            notesMessage = L10n.tr("settings.notes.unlocked_msg")
            notesMessageStyle = .success
        } catch {
            notesMessage = error.localizedDescription
            notesMessageStyle = .error
        }
    }

    private func changeNotesPassword() {
        if let validation = RecoveryPasswordRules.validate(password: notesNewPassword) {
            notesMessage = validation
            notesMessageStyle = .error
            return
        }
        if notesNewPassword != notesConfirmNewPassword {
            notesMessage = L10n.tr("settings.notes.passwords_mismatch")
            notesMessageStyle = .error
            return
        }
        do {
            try appState.noteEncryption.changePassword(oldPassword: notesPassword, newPassword: notesNewPassword)
            notesPassword = ""
            notesNewPassword = ""
            notesConfirmNewPassword = ""
            notesMessage = L10n.tr("settings.notes.updated")
            notesMessageStyle = .success
            Task { await appState.userDataSync.syncAllIfSignedIn() }
        } catch {
            notesMessage = error.localizedDescription
            notesMessageStyle = .error
        }
    }
}
