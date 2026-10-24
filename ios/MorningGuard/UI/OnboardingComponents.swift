import SwiftUI

struct OnboardingStepHeader: View {
    let steps: [String]
    let currentIndex: Int

    var body: some View {
        VStack(spacing: 20) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [MGTheme.accent.opacity(0.35), MGTheme.accent.opacity(0.08)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 72, height: 72)
                Image(systemName: "sun.horizon.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(MGTheme.accent)
            }
            .accessibilityHidden(true)

            Text(L10n.tr("onboarding.header.title"))
                .font(.title.bold())
                .accessibilityAddTraits(.isHeader)

            HStack(spacing: 8) {
                ForEach(Array(steps.enumerated()), id: \.offset) { index, title in
                    stepPill(index: index, title: title)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L10n.tr("a11y.onboarding.header"))
            .accessibilityValue(L10n.tr("a11y.onboarding.step_format", currentIndex + 1, steps.count))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }

    private func stepPill(index: Int, title: String) -> some View {
        let isCurrent = index == currentIndex
        let isComplete = index < currentIndex
        return VStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(isCurrent ? MGTheme.accent : (isComplete ? MGTheme.accent.opacity(0.25) : Color(.tertiarySystemFill)))
                    .frame(width: 28, height: 28)
                if isComplete {
                    Image(systemName: "checkmark")
                        .font(.caption.bold())
                        .foregroundStyle(MGTheme.accent)
                } else {
                    Text("\(index + 1)")
                        .font(.caption.bold())
                        .foregroundStyle(isCurrent ? .white : .secondary)
                }
            }
            Text(title)
                .font(.caption2)
                .foregroundStyle(isCurrent ? .primary : .secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }
}

struct OnboardingSimulatorBanner: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "iphone.gen3")
                .foregroundStyle(MGTheme.accent)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MGTheme.accent.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

struct OnboardingSectionTitle: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.title2.bold())
                .accessibilityAddTraits(.isHeader)
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
