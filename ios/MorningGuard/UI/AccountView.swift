import MorningGuardCore
import SwiftUI

private enum MagicLinkUiPhase {
    case enterEmail
    case awaitingInbox
}

struct AccountView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var email = ""
    @State private var phase: MagicLinkUiPhase = .enterEmail
    @State private var pendingEmail: String?
    @State private var isSending = false
    @State private var resendCooldownSeconds = 0
    @State private var statusMessage: String?
    @State private var errorMessage: String?
    @State private var showDeleteConfirm = false
    @State private var isDeleting = false

    var body: some View {
        Form {
            Section {
                MGSettingsSectionIntro(text: L10n.tr("account.intro"))
            }

            switch appState.supabaseAuth.state {
            case .unconfigured:
                unconfiguredSection
            case .signedIn(let user):
                signedInSection(user: user)
            case .signedOut, .error:
                signInSections
            }

            if let statusMessage, phase != .awaitingInbox {
                Section {
                    Text(statusMessage)
                        .font(.subheadline)
                        .foregroundStyle(MGTheme.accent)
                }
            }
            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .font(.subheadline)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle(L10n.tr("account.title"))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityLabel(L10n.tr("a11y.account.title"))
        .accessibilityHint(L10n.tr("a11y.account.title.hint"))
        .onAppear { syncPhaseFromAuth() }
        .onChange(of: appState.supabaseAuth.state) { _, _ in syncPhaseFromAuth() }
        .task(id: phase) {
            guard phase == .awaitingInbox else { return }
            while !Task.isCancelled {
                refreshCooldown()
                try? await Task.sleep(for: .seconds(1))
            }
        }
        .confirmationDialog(
            L10n.tr("account.delete_confirm.title"),
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button(L10n.tr("account.delete"), role: .destructive) {
                Task { await performDeleteAccount() }
            }
            .accessibilityLabel(L10n.tr("a11y.account.delete_confirm"))
            Button(L10n.tr("common.cancel"), role: .cancel) {}
        } message: {
            Text(L10n.tr("account.delete_confirm.message"))
        }
    }

    @ViewBuilder
    private var unconfiguredSection: some View {
        Section {
            Text(L10n.tr("account.unconfigured"))
                .foregroundStyle(.secondary)
            if let hint = SupabaseConfig.missingPieceMessage {
                Text(hint).font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private func signedInSection(user: AuthUser) -> some View {
        Section {
            Label(user.email ?? L10n.tr("account.signed_in_label"), systemImage: "person.crop.circle")
            MGSettingsSectionIntro(text: L10n.tr("account.sync_enabled"))
            MGSettingsSecondaryButton(title: L10n.tr("account.sign_out")) {
                Task {
                    await appState.signOut()
                    statusMessage = L10n.tr("account.signed_out")
                    errorMessage = nil
                }
            }
            .accessibilityLabel(L10n.tr("a11y.account.sign_out"))
            Button(L10n.tr("account.delete"), role: .destructive) {
                showDeleteConfirm = true
            }
            .disabled(isDeleting)
            .accessibilityLabel(L10n.tr("a11y.account.delete"))
        }
    }

    @ViewBuilder
    private var signInSections: some View {
        switch phase {
        case .enterEmail:
            Section {
                TextField(L10n.tr("account.email"), text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityLabel(L10n.tr("a11y.account.email"))
                Button {
                    Task { await sendMagicLink() }
                } label: {
                    Group {
                        if isSending {
                            ProgressView()
                        } else {
                            Text(L10n.tr("account.send_link"))
                                .font(.body.weight(.semibold))
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(MGTheme.accent)
                .disabled(isSending || email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .accessibilityLabel(L10n.tr("a11y.account.send_link"))
            } header: {
                Text(L10n.tr("account.magic_link.header"))
            } footer: {
                Text(L10n.tr("account.magic_link.footer"))
                    .font(.footnote)
            }
        case .awaitingInbox:
            Section {
                Label(L10n.tr("account.inbox.check"), systemImage: "envelope.open")
                Text(pendingEmail ?? email)
                    .font(.headline)
                Text(L10n.tr("account.inbox.tap_link"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button {
                    Task { await sendMagicLink(resend: true) }
                } label: {
                    if isSending {
                        ProgressView()
                    } else if resendCooldownSeconds > 0 {
                        Text(L10n.tr("account.resend_format", resendCooldownSeconds))
                    } else {
                        Text(L10n.tr("account.resend_link"))
                    }
                }
                .disabled(isSending || resendCooldownSeconds > 0)
                .accessibilityLabel(L10n.tr("a11y.account.resend_link"))
                Button(L10n.tr("account.different_email")) {
                    phase = .enterEmail
                    pendingEmail = nil
                    email = ""
                    errorMessage = nil
                    statusMessage = nil
                }
                .accessibilityLabel(L10n.tr("a11y.account.different_email"))
            }
        }
    }

    private func syncPhaseFromAuth() {
        if case .signedIn = appState.supabaseAuth.state {
            phase = .enterEmail
            pendingEmail = nil
            errorMessage = nil
        }
    }

    private func refreshCooldown() {
        let target = pendingEmail ?? email
        let seconds = MagicLinkRateLimitStore.secondsUntilResend(email: target)
        if resendCooldownSeconds != seconds {
            resendCooldownSeconds = seconds
        }
    }

    private func sendMagicLink(resend: Bool = false) async {
        let normalized = (resend ? (pendingEmail ?? email) : email)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard normalized.contains("@") else {
            errorMessage = L10n.tr("account.invalid_email")
            return
        }
        let clientWait = MagicLinkRateLimitStore.secondsUntilResend(email: normalized)
        if clientWait > 0 {
            phase = .awaitingInbox
            pendingEmail = normalized
            resendCooldownSeconds = clientWait
            errorMessage = L10n.tr("account.rate_limit_format", clientWait)
            return
        }
        isSending = true
        errorMessage = nil
        statusMessage = nil
        pendingEmail = normalized
        do {
            try await appState.sendMagicLink(email: normalized)
            phase = .awaitingInbox
            resendCooldownSeconds = MagicLinkRateLimitStore.secondsUntilResend(email: normalized)
            statusMessage = L10n.tr("account.link_sent_format", normalized)
        } catch let limited as MagicLinkRateLimitedError {
            phase = .awaitingInbox
            resendCooldownSeconds = limited.retryAfterSeconds
            errorMessage = limited.localizedDescription
        } catch let auth as AuthServiceError {
            if case .tooManyEmails = auth {
                errorMessage = auth.localizedDescription
                phase = .awaitingInbox
            } else {
                errorMessage = auth.localizedDescription
                if !resend { phase = .enterEmail }
            }
        } catch {
            errorMessage = error.localizedDescription
            if !resend { phase = .enterEmail }
        }
        isSending = false
    }

    private func performDeleteAccount() async {
        isDeleting = true
        errorMessage = nil
        do {
            try await appState.deleteAccount()
            statusMessage = L10n.tr("account.deleted")
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isDeleting = false
    }
}
