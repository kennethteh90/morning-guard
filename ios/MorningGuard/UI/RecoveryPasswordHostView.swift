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
                "Start fresh on this phone?",
                isPresented: $showStartFreshConfirm,
                titleVisibility: .visible
            ) {
                Button("Start fresh on this device", role: .destructive) {
                    appState.recoveryPassword.startFreshWithoutCloudNotes()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text(
                    "Notes encrypted with your old recovery password cannot be opened without that password. "
                        + "Your cloud data will not be deleted, but this device will create new notes with a new key. "
                        + "Only continue if you accept losing access to older cloud notes."
                )
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
                    Text(
                        "Your account has encrypted notes from another device. "
                            + "Enter your recovery password to open them here."
                    )
                    SecureField("Recovery password", text: $password)
                    if let error = appState.recoveryPassword.errorMessage {
                        Text(error).foregroundStyle(.red).font(.caption)
                    }
                    Button("Forgot password? Start fresh on this device") {
                        showStartFreshConfirm = true
                    }
                }
                Section {
                    Button("Unlock") {
                        appState.recoveryPassword.unlockCloudRecovery(password: password)
                        password = ""
                    }
                }
            }
            .navigationTitle("Enter recovery password")
            .interactiveDismissDisabled()
        }
    }

    private var dedicatedRecoverySheet: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Protect your notes on a new phone")
                        .font(.headline)
                    Text(
                        "Your reflection notes are encrypted with a key that stays on this phone. "
                            + "Without a recovery password, reinstalling or switching phones means "
                            + "cloud copies cannot be opened."
                    )
                    .font(.subheadline)
                    Text("Use at least 8 characters with a letter and a number. Save it in a password manager.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    SecureField("Recovery password", text: $password)
                    SecureField("Confirm recovery password", text: $confirmation)
                    if let error = appState.recoveryPassword.errorMessage {
                        Text(error).foregroundStyle(.red).font(.caption)
                    }
                    if let status = appState.recoveryPassword.statusMessage {
                        Text(status).foregroundStyle(MGTheme.accent).font(.caption)
                    }
                }
                Section {
                    Button("Set recovery password") {
                        appState.recoveryPassword.setRecoveryPassword(password: password, confirmation: confirmation)
                        password = ""
                        confirmation = ""
                        appState.recoveryPassword.markPromptShown()
                    }
                    .disabled(password.count < 8 || confirmation.count < 8)
                    Button("Later — I may lose notes on a new phone") {
                        appState.recoveryPassword.dismissLater()
                        appState.recoveryPassword.markPromptShown()
                    }
                }
            }
            .navigationTitle("Recovery password")
        }
    }
}

struct RecoveryPasswordBannerView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        if appState.recoveryPassword.showBanner, !appState.noteEncryption.cryptoState.hasRecoveryPassword() {
            VStack(alignment: .leading, spacing: 6) {
                Text("Recovery password not set").font(.subheadline.weight(.semibold))
                Text("Notes are encrypted on this device but cannot be recovered on a new phone until you set one.")
                    .font(.caption)
                Button("Set recovery password") {
                    appState.recoveryPassword.onSignedIn()
                }
                .font(.caption)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(MGTheme.accent.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }
}
