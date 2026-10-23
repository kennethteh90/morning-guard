import SwiftUI

enum MGTheme {
    static let accent = Color(red: 0.92, green: 0.55, blue: 0.20)
    static let surface = Color(.secondarySystemGroupedBackground)
    static let background = Color(.systemGroupedBackground)

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

/// Full-width primary action cell inside an inset grouped list section.
struct MGListPrimaryActionLabel: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.body.weight(.semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.vertical, 4)
    }
}
