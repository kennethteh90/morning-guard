import SwiftUI

struct LocalBackupPasswordSheet: View {
    enum Mode {
        case export
        case importFile

        var title: String {
            switch self {
            case .export: L10n.tr("backup.sheet.encrypt_title")
            case .importFile: L10n.tr("backup.sheet.decrypt_title")
            }
        }

        var confirmLabel: String {
            switch self {
            case .export: L10n.tr("backup.sheet.export_action")
            case .importFile: L10n.tr("backup.sheet.restore_action")
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
                Text(L10n.tr("backup.sheet.password_hint"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                SecureField(L10n.tr("backup.sheet.password"), text: $password)
                    .accessibilityLabel(L10n.tr("a11y.backup.password"))
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
                    Button(L10n.tr("common.cancel")) { dismiss() }
                        .accessibilityLabel(L10n.tr("a11y.backup.cancel"))
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(mode.confirmLabel) { submit() }
                        .accessibilityLabel(L10n.tr("a11y.backup.confirm"))
                }
            }
        }
    }

    private func submit() {
        guard password.count >= 8 else {
            error = L10n.tr("backup.sheet.min_length")
            return
        }
        onConfirm(password)
        dismiss()
    }
}
