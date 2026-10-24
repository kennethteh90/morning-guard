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
                Text("Sign in to sync streaks and highlights across devices. Your reflection notes stay encrypted; set a recovery password in Settings.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
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
        .navigationTitle("Account")
        .navigationBarTitleDisplayMode(.inline)
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
            "Delete account?",
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button("Delete account", role: .destructive) {
                Task { await performDeleteAccount() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently deletes your Morning Guard account and cloud data. Local streaks and highlights on this device will be cleared.")
        }
    }

    @ViewBuilder
    private var unconfiguredSection: some View {
        Section {
            Text("Cloud sync is not configured in this build.")
                .foregroundStyle(.secondary)
            if let hint = SupabaseConfig.missingPieceMessage {
                Text(hint).font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private func signedInSection(user: AuthUser) -> some View {
        Section {
            Label(user.email ?? "Signed in", systemImage: "person.crop.circle")
            Text("Streaks and highlights sync while cloud sync is enabled.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Button("Sign out", role: .destructive) {
                Task {
                    await appState.signOut()
                    statusMessage = "Signed out on this device."
                    errorMessage = nil
                }
            }
            Button("Delete account", role: .destructive) {
                showDeleteConfirm = true
            }
            .disabled(isDeleting)
        }
    }

    @ViewBuilder
    private var signInSections: some View {
        switch phase {
        case .enterEmail:
            Section {
                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button {
                    Task { await sendMagicLink() }
                } label: {
                    if isSending {
                        ProgressView()
                    } else {
                        Text("Send sign-in link")
                    }
                }
                .disabled(isSending || email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            } header: {
                Text("Magic link")
            } footer: {
                Text("Open the link on this iPhone. Check spam if it does not arrive within a minute.")
            }
        case .awaitingInbox:
            Section {
                Label("Check your inbox", systemImage: "envelope.open")
                Text(pendingEmail ?? email)
                    .font(.headline)
                Text("Tap the link in the email to finish signing in.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button {
                    Task { await sendMagicLink(resend: true) }
                } label: {
                    if isSending {
                        ProgressView()
                    } else if resendCooldownSeconds > 0 {
                        Text("Resend in \(resendCooldownSeconds)s")
                    } else {
                        Text("Resend link")
                    }
                }
                .disabled(isSending || resendCooldownSeconds > 0)
                Button("Use a different email") {
                    phase = .enterEmail
                    pendingEmail = nil
                    email = ""
                    errorMessage = nil
                    statusMessage = nil
                }
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
            errorMessage = "Enter a valid email address."
            return
        }
        let clientWait = MagicLinkRateLimitStore.secondsUntilResend(email: normalized)
        if clientWait > 0 {
            phase = .awaitingInbox
            pendingEmail = normalized
            resendCooldownSeconds = clientWait
            errorMessage = "Please wait \(clientWait) seconds before requesting another link."
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
            statusMessage = "We sent a link to \(normalized). Open it on this iPhone."
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
            statusMessage = "Your account was deleted."
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isDeleting = false
    }
}
