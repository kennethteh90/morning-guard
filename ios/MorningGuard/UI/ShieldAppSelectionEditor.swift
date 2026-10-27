import SwiftUI

#if canImport(FamilyControls)
import FamilyControls

/// Screen Time app/category picker shared by onboarding and Settings.
struct ShieldAppSelectionEditor: View {
    enum PresentationStyle {
        case onboardingCard
        case settingsRow
    }

    @Binding var selection: FamilyActivitySelection
    var style: PresentationStyle
    var onSelectionChanged: (() -> Void)?

    @State private var isPickerPresented = false

    var body: some View {
        Group {
            switch style {
            case .onboardingCard:
                onboardingCard
            case .settingsRow:
                settingsRow
            }
        }
        .familyActivityPicker(isPresented: $isPickerPresented, selection: $selection)
        .onChange(of: selection) { _, newValue in
            selection = newValue
            onSelectionChanged?()
        }
    }

    static func summary(for selection: FamilyActivitySelection) -> String {
        let apps = selection.applicationTokens.count
        let categories = selection.categoryTokens.count
        let domains = selection.webDomainTokens.count
        if apps == 0 && categories == 0 && domains == 0 {
            return L10n.tr("onboarding.apps.none_selected")
        }
        return L10n.tr("onboarding.apps.selected_summary", apps, categories, domains)
    }

    private var onboardingCard: some View {
        MGCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(Self.summary(for: selection))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Button {
                    isPickerPresented = true
                } label: {
                    Label(L10n.tr("onboarding.apps.choose"), systemImage: "apps.iphone")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(MGTheme.accent)
                .accessibilityLabel(L10n.tr("a11y.onboarding.app_picker"))
                .accessibilityHint(L10n.tr("onboarding.apps.choose.hint"))
            }
        }
    }

    private var settingsRow: some View {
        Button {
            isPickerPresented = true
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.tr("settings.shields.edit_apps"))
                        .foregroundStyle(.primary)
                    Text(Self.summary(for: selection))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
        }
        .accessibilityLabel(L10n.tr("a11y.settings.edit_shielded_apps"))
        .accessibilityHint(L10n.tr("settings.shields.edit_apps.hint"))
    }
}
#endif
