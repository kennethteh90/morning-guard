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
                Text("Enter your emergency passphrase to unlock apps for one hour. Failed attempts are rate limited.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            Section("Passphrase") {
                SecureField("Passphrase", text: $passphrase)
                Button("Unlock for 1 hour") { attemptBypass() }
            }
            if let message {
                Section {
                    Text(message)
                        .foregroundStyle(isError ? .red : .secondary)
                }
            }
        }
        .navigationTitle("Emergency bypass")
    }

    private func attemptBypass() {
        do {
            let expires = try appState.attemptEmergencyBypass(passphrase: passphrase)
            let date = Date(timeIntervalSince1970: TimeInterval(expires) / 1000)
            message = "Bypass active until \(date.formatted(date: .omitted, time: .shortened))."
            isError = false
            passphrase = ""
        } catch {
            message = error.localizedDescription
            isError = true
        }
    }
}
