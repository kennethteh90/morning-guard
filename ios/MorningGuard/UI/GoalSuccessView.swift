import SwiftUI

struct GoalSuccessView: View {
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "sun.max.fill")
                .font(.system(size: 56))
                .foregroundStyle(MGTheme.accent)
                .accessibilityHidden(true)
            Text("Morning goal complete")
                .font(.largeTitle.bold())
            Text("Your selected apps are unlocked for today. Have a grounded morning.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, MGTheme.standardPadding)
            Spacer()
            Button("Back to home", action: onDone)
                .buttonStyle(.borderedProminent)
                .padding(MGTheme.standardPadding)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(MGTheme.background)
        .navigationBarBackButtonHidden(true)
    }
}
