import SwiftUI
import MorningGuardCore

/// Shared Bible reader used on the morning goal and Read Bible flows (Android `browseOnly` parity).
struct BibleReadingScreen: View {
    @Environment(AppState.self) private var appState

    var browseOnly: Bool
    var showsGoalProgress: Bool
    var showsNavigationChrome: Bool = false
    var elapsedSeconds: Int = 0
    var requiredSeconds: Int = 60

    @State private var passage: BibleChapterPassage?
    @State private var loading = false
    @State private var errorMessage: String?
    @State private var highlightBarState: VerseHighlightBarState?
    @State private var translationSheet = false
    @State private var bookChapterSheet = false
    @State private var banner: MGBannerPayload?
    @AppStorage("mg_bibleReaderTipSeen") private var bibleReaderTipSeen = false
    @State private var loadedChapterKey: String?
    @State private var prefetchTask: Task<Void, Never>?

    private var config: MorningGoalConfig {
        appState.settingsStore.userSettings.morningGoal
    }

    private var chapterHighlights: [BibleHighlight] {
        appState.bibleHighlightStore.highlightsForChapter(
            translationId: config.bibleTranslation,
            book: config.bibleBook,
            chapter: config.bibleChapter
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let errorMessage, !loading {
                    bibleLoadErrorBanner(errorMessage)
                }
                if showsGoalProgress {
                    ProgressView(value: min(1, Double(elapsedSeconds) / Double(requiredSeconds)))
                    Text(L10n.tr("bible.reading.progress_format", elapsedSeconds, requiredSeconds))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(L10n.tr("a11y.goal.progress"))
                }

                if !bibleReaderTipSeen {
                    bibleTipCard
                }

                if loading {
                    ProgressView(L10n.tr("bible.loading_chapter"))
                        .frame(maxWidth: .infinity)
                } else if errorMessage != nil {
                    EmptyView()
                } else if let passage, !passage.blocks.isEmpty {
                    BiblePassageView(
                        blocks: passage.blocks,
                        savedHighlights: chapterHighlights,
                        highlightBarState: $highlightBarState,
                        defaultHighlightColorKey: appState.bibleHighlightStore.lastHighlightColorKey,
                        decryptNote: { appState.bibleHighlightStore.displayNote(for: $0) }
                    )
                } else {
                    Text(L10n.tr("bible.chapter_not_loaded"))
                        .foregroundStyle(.secondary)
                }
            }
            .padding(MGTheme.standardPadding)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            bottomChrome
                .animation(.spring(duration: 0.35), value: highlightBarState != nil)
        }
        .overlay(alignment: .top) {
            MGBannerOverlay(payload: banner)
        }
        .toolbar {
            if showsNavigationChrome {
                ToolbarItem(placement: .principal) {
                    Button {
                        bookChapterSheet = true
                    } label: {
                        Text("\(config.bibleBook) \(config.bibleChapter)")
                            .font(.headline)
                    }
                    .accessibilityLabel(L10n.tr("a11y.bible.chapter_nav"))
                    .accessibilityHint(L10n.tr("a11y.bible.book_picker"))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        translationSheet = true
                    } label: {
                        Text(BibleTranslations.chipLabel(id: config.bibleTranslation))
                            .font(.subheadline.weight(.semibold))
                    }
                    .accessibilityLabel(L10n.tr("a11y.bible.translation_picker"))
                }
            }
        }
        .task(id: chapterLoadKey) { await loadChapter(forceRefresh: false) }
        .sheet(isPresented: $translationSheet) {
            BibleTranslationPickerSheet(
                selectedTranslationId: config.bibleTranslation,
                onSelect: { appState.setBibleTranslation($0) }
            )
        }
        .sheet(isPresented: $bookChapterSheet) {
            BibleBookChapterPickerSheet(
                selectedBook: config.bibleBook,
                selectedChapter: config.bibleChapter,
                onSelect: { book, chapter in
                    appState.setBibleReading(book: book, chapter: chapter)
                }
            )
        }
    }

    @ViewBuilder
    private var bottomChrome: some View {
        if let barState = highlightBarState {
            VerseHighlightBottomBar(
                state: Binding(
                    get: { barState },
                    set: { highlightBarState = $0 }
                ),
                onSave: saveHighlight,
                onDelete: deleteHighlight,
                onCopy: copySelection,
                onDismiss: {
                    withAnimation(.spring(duration: 0.35)) {
                        highlightBarState = nil
                    }
                },
                onColorSelected: { appState.bibleHighlightStore.setLastHighlightColorKey($0) }
            )
            .transition(.move(edge: .bottom).combined(with: .opacity))
        } else {
            chapterNavBar
                .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    private var bibleTipCard: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "hand.tap.fill")
                .foregroundStyle(MGTheme.accent)
            VStack(alignment: .leading, spacing: 6) {
                Text(L10n.tr("bible.tip.body"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Button(L10n.tr("bible.tip.dismiss")) { bibleReaderTipSeen = true }
                    .font(.subheadline.weight(.semibold))
                    .accessibilityLabel(L10n.tr("a11y.bible.tip.dismiss"))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MGTheme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(L10n.tr("a11y.bible.tip"))
    }

    private var chapterLoadKey: String {
        "\(config.bibleTranslation)|\(config.bibleBook)|\(config.bibleChapter)"
    }

    private func bibleLoadErrorBanner(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(
                L10n.tr(
                    "bible.load_error.title_format",
                    config.bibleBook,
                    config.bibleChapter,
                    BibleTranslations.chipLabel(id: config.bibleTranslation)
                )
            )
            .font(.subheadline.bold())
            Text(message)
                .font(.subheadline)
            Button(L10n.tr("bible.retry")) { Task { await loadChapter(forceRefresh: true) } }
                .buttonStyle(.bordered)
                .accessibilityLabel(L10n.tr("a11y.bible.retry"))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.red.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(L10n.tr("a11y.bible.load_error"))
    }

    private var chapterNavBar: some View {
        MGBottomAccessory {
            HStack(spacing: 4) {
                chapterNavCaretButton(
                    systemName: "chevron.left",
                    accessibilityLabel: L10n.tr("a11y.bible.previous_chapter"),
                    action: { appState.stepBibleChapter(delta: -1) }
                )
                Spacer(minLength: 8)
                if !showsNavigationChrome {
                    Button {
                        bookChapterSheet = true
                    } label: {
                        Text("\(config.bibleBook) \(config.bibleChapter)")
                            .font(.subheadline.bold())
                            .lineLimit(1)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L10n.tr("a11y.bible.chapter_nav"))
                    .accessibilityHint(L10n.tr("a11y.bible.book_picker"))
                } else {
                    Text(BibleTranslations.chipLabel(id: config.bibleTranslation))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                chapterNavCaretButton(
                    systemName: "chevron.right",
                    accessibilityLabel: L10n.tr("a11y.bible.next_chapter"),
                    action: { appState.stepBibleChapter(delta: 1) }
                )
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
        }
    }

    private func chapterNavCaretButton(
        systemName: String,
        accessibilityLabel: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }

    private func loadChapter(forceRefresh: Bool) async {
        let key = chapterLoadKey
        if !forceRefresh, key == loadedChapterKey, let passage, !passage.blocks.isEmpty {
            return
        }
        let chapterChanged = key != loadedChapterKey
        loading = true
        errorMessage = nil
        if chapterChanged {
            passage = nil
            withAnimation(.spring(duration: 0.35)) {
                highlightBarState = nil
            }
        }
        defer { loading = false }
        do {
            let loaded = try await appState.bibleClient.loadChapter(
                translation: config.bibleTranslation,
                book: config.bibleBook,
                chapter: config.bibleChapter,
                forceRefresh: forceRefresh
            )
            loadedChapterKey = key
            passage = loaded
            prefetchAdjacentChapters()
        } catch {
            if chapterChanged {
                passage = nil
            }
            errorMessage = friendlyBibleError(error.localizedDescription)
        }
    }

    private func prefetchAdjacentChapters() {
        prefetchTask?.cancel()
        let translation = config.bibleTranslation
        prefetchTask = Task {
            let (nextBook, nextChapter) = BibleCanon.advance(bookName: config.bibleBook, chapter: config.bibleChapter)
            await appState.bibleClient.prefetchChapter(
                translation: translation,
                book: nextBook,
                chapter: nextChapter
            )
            let (prevBook, prevChapter) = BibleCanon.previous(bookName: config.bibleBook, chapter: config.bibleChapter)
            await appState.bibleClient.prefetchChapter(
                translation: translation,
                book: prevBook,
                chapter: prevChapter
            )
        }
    }

    private func friendlyBibleError(_ error: String) -> String {
        if error.contains("-1011") || error.localizedCaseInsensitiveContains("badServerResponse") {
            return L10n.tr("bible.error.web_fallback")
        }
        return error
    }

    private func saveHighlight() {
        guard let bar = highlightBarState, bar.existingHighlight == nil else { return }
        let sorted = bar.verseSnippets.filter { bar.selectedVerses.contains($0.number) }.sorted { $0.number < $1.number }
        guard !sorted.isEmpty else { return }
        let numbers = sorted.map(\.number)
        let excerpt = BibleVerseCopyFormat.formatBody(verses: sorted.map { BibleVerseLine(number: $0.number, text: $0.text) })
            .prefix(4_000)
        let label = BibleVerseReferenceFormat.chapterLabel(
            book: config.bibleBook,
            chapter: config.bibleChapter,
            verses: numbers,
            translationLabel: BibleTranslations.chipLabel(id: config.bibleTranslation)
        )
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        appState.bibleHighlightStore.setLastHighlightColorKey(bar.colorKey)
        appState.bibleHighlightStore.saveHighlight(
            BibleHighlight(
                translationId: config.bibleTranslation,
                book: config.bibleBook,
                chapter: config.bibleChapter,
                label: label,
                excerpt: String(excerpt),
                verse: numbers.first,
                verseEnd: numbers.last,
                verseNumbers: numbers.map(String.init).joined(separator: ","),
                colorKey: bar.colorKey,
                note: bar.note.trimmingCharacters(in: .whitespacesAndNewlines),
                createdAtEpochMs: nowMs
            ),
            onNoteSaved: {
                appState.recoveryPassword.onNoteSaved()
                Task { await appState.userDataSync.syncAllIfSignedIn() }
            }
        )
        Task { await appState.userDataSync.syncAllIfSignedIn() }
        MGHaptics.success()
        withAnimation(.spring(duration: 0.35)) {
            highlightBarState = nil
        }
        MGBanner.present(.message(L10n.tr("bible.saved_highlight")), binding: $banner)
    }

    private func deleteHighlight() {
        guard let existing = highlightBarState?.existingHighlight else { return }
        MGHaptics.lightImpact()
        withAnimation(.spring(duration: 0.35)) {
            highlightBarState = nil
        }
        MGBanner.present(.message(L10n.tr("bible.highlight_removed")), binding: $banner)
        Task { await appState.userDataSync.deleteHighlight(existing) }
    }

    private func copySelection() {
        guard let bar = highlightBarState else { return }
        let selected = bar.verseSnippets.filter { bar.selectedVerses.contains($0.number) }
        let text = BibleVerseCopyFormat.format(
            book: config.bibleBook,
            chapter: config.bibleChapter,
            translationId: config.bibleTranslation,
            verses: selected.map { BibleVerseLine(number: $0.number, text: $0.text) }
        )
        #if canImport(UIKit)
        UIPasteboard.general.string = text
        #endif
        MGHaptics.lightImpact()
        MGBanner.present(.message(L10n.tr("bible.copied")), binding: $banner)
    }
}

#if canImport(UIKit)
import UIKit
#endif
