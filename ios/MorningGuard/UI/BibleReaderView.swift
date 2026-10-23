import SwiftUI

struct BibleReaderView: View {
    var body: some View {
        BibleReadingScreen(browseOnly: true, showsGoalProgress: false)
            .background(MGTheme.background)
            .navigationTitle("Read Bible")
            .navigationBarTitleDisplayMode(.inline)
    }
}
