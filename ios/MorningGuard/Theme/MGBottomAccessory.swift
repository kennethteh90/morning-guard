import SwiftUI

/// Rounded top sheet styling for bottom safe-area accessories (highlight bar, chapter nav).
struct MGBottomAccessory<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .frame(maxWidth: .infinity)
            .background {
                UnevenRoundedRectangle(cornerRadii: .init(topLeading: 20, topTrailing: 20))
                    .fill(.regularMaterial)
                    .shadow(color: .black.opacity(0.12), radius: 12, y: -4)
            }
    }
}
