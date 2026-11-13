import SwiftUI

enum MGTheme {
    static let accent = Color(red: 0.92, green: 0.55, blue: 0.20)
    static let surface = Color(.secondarySystemGroupedBackground)
    static let background = Color(.systemGroupedBackground)

    static let error = Color(.systemRed)
    static let errorBackground = Color(.systemRed).opacity(0.12)
    static let warning = Color(.systemOrange)
    static let warningBackground = Color(.systemOrange).opacity(0.12)
    static let destructive = Color(.systemRed)
    static let destructiveBackground = Color(.systemRed).opacity(0.12)
    /// Text/icon on filled accent or primary action backgrounds.
    static let onAccent = Color.white
    static let disabledControl = Color(.systemGray3)

    static let titleFont = Font.title2.weight(.semibold)
    static let bodyFont = Font.body
    static let captionFont = Font.subheadline

    static let standardPadding: CGFloat = 20
    static let cardCornerRadius: CGFloat = 16
}

struct MGCard<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(MGTheme.standardPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(MGTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: MGTheme.cardCornerRadius, style: .continuous))
    }
}

/// Standard Settings-style row (avoids tinted capsule `Button` styling inside `List`).
struct MGListNavigationRow: View {
    let title: String
    let systemImage: String

    var body: some View {
        HStack(spacing: 12) {
            Label {
                Text(title)
                    .foregroundStyle(.primary)
            } icon: {
                Image(systemName: systemImage)
                    .foregroundStyle(MGTheme.accent)
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .contentShape(Rectangle())
    }
}

// MARK: - Accessibility

enum MGAccessibility {
    static let minTapDimension: CGFloat = 44
}

extension View {
    /// Ensures a tappable control meets the 44×44 pt minimum.
    func mgMinimumTapTarget(alignment: Alignment = .center) -> some View {
        frame(minWidth: MGAccessibility.minTapDimension, minHeight: MGAccessibility.minTapDimension, alignment: alignment)
            .contentShape(Rectangle())
    }

    /// Keeps layouts usable through the largest Dynamic Type sizes without unbounded growth on hero metrics.
    func mgReadableDynamicType() -> some View {
        dynamicTypeSize(.xSmall ... .accessibility3)
    }
}

/// Full-width primary action cell inside an inset grouped list section.
struct MGListPrimaryActionLabel: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.body.weight(.semibold))
            .foregroundStyle(MGTheme.onAccent)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.vertical, 4)
    }
}
