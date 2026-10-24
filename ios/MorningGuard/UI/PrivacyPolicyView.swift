import SwiftUI

/// In-app disclosure aligned with Android [PrivacyPolicyActivity] (notification listener text is Android-only).
struct PrivacyPolicyView: View {
    static var policyURL: URL? {
        SettingsView.privacyPolicyURL
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.tr("privacy.heading"))
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)

                Text(L10n.tr("privacy.screentime"))
                    .font(.body)

                Text(L10n.tr("privacy.healthkit"))
                    .font(.body)
                    .foregroundStyle(.secondary)

                Text(L10n.tr("privacy.cloud"))
                    .font(.body)
                    .foregroundStyle(.secondary)

                if let url = Self.policyURL {
                    Link(L10n.tr("privacy.web_link"), destination: url)
                        .font(.body.weight(.semibold))
                        .accessibilityLabel(L10n.tr("a11y.privacy.web_link"))
                }
            }
            .padding(MGTheme.standardPadding)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(L10n.tr("a11y.privacy.body"))
        }
        .background(MGTheme.background)
        .navigationTitle(L10n.tr("privacy.title"))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityLabel(L10n.tr("a11y.privacy.title"))
    }
}
