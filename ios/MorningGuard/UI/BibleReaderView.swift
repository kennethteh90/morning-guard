import SwiftUI

struct BibleReaderView: View {
    var body: some View {
        BibleReadingScreen(browseOnly: true, showsGoalProgress: false, showsNavigationChrome: true)
            .background(MGTheme.background)
            .navigationTitle(L10n.tr("bible.reader.title"))
            .navigationBarTitleDisplayMode(.inline)
            .accessibilityLabel(L10n.tr("a11y.bible.reader"))
            .accessibilityHint(L10n.tr("a11y.bible.reader.hint"))
    }
}
