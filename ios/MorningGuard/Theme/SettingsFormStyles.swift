import SwiftUI

/// Explanatory copy at the top of a settings section (not a tappable row).
struct MGSettingsSectionIntro: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isStaticText)
    }
}

/// Short label above a cluster of fields inside a section.
struct MGSettingsSubsectionTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.secondary)
            .textCase(.uppercase)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 4)
    }
}

struct MGSettingsChecklistRow: View {
    let step: Int
    let title: String
    let detail: String
    let isComplete: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            stepBadge
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var stepBadge: some View {
        ZStack {
            Circle()
                .fill(isComplete ? MGTheme.accent : Color(.tertiarySystemFill))
                .frame(width: 28, height: 28)
            if isComplete {
                Image(systemName: "checkmark")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
            } else {
                Text("\(step)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityHidden(true)
    }
}

struct MGSettingsStatusBanner: View {
    enum Tone {
        case ready
        case actionNeeded
        case neutral
    }

    let title: String
    let tone: Tone

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: iconName)
                .font(.body.weight(.semibold))
                .foregroundStyle(foreground)
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(foreground)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
        .listRowBackground(Color.clear)
    }

    private var iconName: String {
        switch tone {
        case .ready: "checkmark.circle.fill"
        case .actionNeeded: "exclamationmark.circle.fill"
        case .neutral: "info.circle.fill"
        }
    }

    private var foreground: Color {
        switch tone {
        case .ready: MGTheme.accent
        case .actionNeeded: .orange
        case .neutral: .secondary
        }
    }

    private var background: Color {
        switch tone {
        case .ready: MGTheme.accent.opacity(0.12)
        case .actionNeeded: Color.orange.opacity(0.12)
        case .neutral: Color(.tertiarySystemFill)
        }
    }
}

struct MGSettingsPrimaryButton: View {
    let title: String
    var disabled: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(MGTheme.accent)
        .disabled(disabled)
    }
}

struct MGSettingsSecondaryButton: View {
    let title: String
    var disabled: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.medium))
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(MGTheme.accent)
        .disabled(disabled)
    }
}

struct MGSettingsInlineMessage: View {
    enum Style {
        case neutral
        case success
        case error
    }

    let text: String
    var style: Style = .neutral

    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(foreground)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var foreground: Color {
        switch style {
        case .neutral: .secondary
        case .success: MGTheme.accent
        case .error: .red
        }
    }
}
