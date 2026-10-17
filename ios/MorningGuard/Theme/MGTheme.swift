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
