import SwiftUI

struct EmergencyBypassView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var passphrase = ""
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        Form {
            Section {
                Text(L10n.tr("emergency.intro"))
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel(L10n.tr("emergency.intro"))
            }
            Section(L10n.tr("emergency.passphrase_section")) {
                SecureField(L10n.tr("emergency.passphrase_field"), text: $passphrase)
                    .accessibilityLabel(L10n.tr("a11y.emergency.passphrase"))
                Button(L10n.tr("emergency.unlock_button")) { attemptBypass() }
                    .accessibilityLabel(L10n.tr("a11y.emergency.unlock"))
                    .accessibilityHint(L10n.tr("a11y.emergency.unlock.hint"))
            }
            if let message {
                Section {
                    Text(message)
                        .foregroundStyle(isError ? .red : .secondary)
                }
            }
        }
        .navigationTitle(L10n.tr("emergency.title"))
        .accessibilityLabel(L10n.tr("a11y.emergency.title"))
        .accessibilityHint(L10n.tr("a11y.emergency.title.hint"))
    }

    private func attemptBypass() {
        do {
            let expires = try appState.attemptEmergencyBypass(passphrase: passphrase)
            let date = Date(timeIntervalSince1970: TimeInterval(expires) / 1000)
            message = L10n.tr("emergency.success_format", date.formatted(date: .omitted, time: .shortened))
            isError = false
            passphrase = ""
        } catch {
            message = error.localizedDescription
            isError = true
        }
    }
}
