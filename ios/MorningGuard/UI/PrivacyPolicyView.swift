import SwiftUI

/// In-app disclosure aligned with Android [PrivacyPolicyActivity] (notification listener text is Android-only).
struct PrivacyPolicyView: View {
    static var policyURL: URL? {
        SettingsView.privacyPolicyURL
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Privacy")
                    .font(.title2.bold())

                Text(
                    "Morning Guard on iPhone uses Apple’s Screen Time APIs to shield apps you choose during your morning window. " +
                    "It does not read, hold, or summarize notifications from other apps (unlike Android’s digest)."
                )
                .font(.body)

                Text(
                    "Morning Guard may read step counts through HealthKit when you choose a step goal. " +
                    "Step data is used only on this device to verify your goal."
                )
                .font(.body)
                .foregroundStyle(.secondary)

                Text(
                    "If you enable cloud sync, streak and highlight metadata sync through Supabase under your account. " +
                    "Highlight reflection notes are encrypted on device; only ciphertext syncs when you set a recovery password."
                )
                .font(.body)
                .foregroundStyle(.secondary)

                if let url = Self.policyURL {
                    Link("Full privacy policy on the web", destination: url)
                        .font(.body.weight(.semibold))
                }
            }
            .padding(MGTheme.standardPadding)
        }
        .background(MGTheme.background)
        .navigationTitle("Privacy policy")
        .navigationBarTitleDisplayMode(.inline)
    }
}
