import SwiftUI

struct LocalBackupPasswordSheet: View {
    enum Mode {
        case export
        case importFile

        var title: String {
            switch self {
            case .export: "Encrypt backup"
            case .importFile: "Decrypt backup"
            }
        }

        var confirmLabel: String {
            switch self {
            case .export: "Export"
            case .importFile: "Restore"
            }
        }
    }

    let mode: Mode
    let onConfirm: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Text("Use a strong password. Without it, the backup cannot be restored.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                SecureField("Backup password", text: $password)
                if let error {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.caption)
                }
            }
            .navigationTitle(mode.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(mode.confirmLabel) { submit() }
                }
            }
        }
    }

    private func submit() {
        guard password.count >= 8 else {
            error = "Use at least 8 characters."
            return
        }
        onConfirm(password)
        dismiss()
    }
}
