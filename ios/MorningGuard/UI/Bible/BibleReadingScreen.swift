import SwiftUI
import MorningGuardCore

/// Shared Bible reader used on the morning goal and Read Bible flows (Android `browseOnly` parity).
struct BibleReadingScreen: View {
    @Environment(AppState.self) private var appState

    var browseOnly: Bool
    var showsGoalProgress: Bool
    var elapsedSeconds: Int = 0
    var requiredSeconds: Int = 60

    @State private var passage: BibleChapterPassage?
    @State private var loading = false
    @State private var errorMessage: String?
    @State private var highlightBarState: VerseHighlightBarState?
    @State private var translationSheet = false
    @State private var bookChapterSheet = false
    @State private var savedToast = false

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
                selectorRow
                if showsGoalProgress {
                    ProgressView(value: min(1, Double(elapsedSeconds) / Double(requiredSeconds)))
                    Text("\(elapsedSeconds) / \(requiredSeconds) seconds on screen")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text("Tap verses to highlight. Use the bottom bar to save, add a note, or delete.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if loading {
                    ProgressView("Loading chapter…")
                        .frame(maxWidth: .infinity)
                } else if let errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                    Button("Retry") { Task { await loadChapter() } }
                        .buttonStyle(.bordered)
                } else if let passage, !passage.blocks.isEmpty {
                    BiblePassageView(
                        blocks: passage.blocks,
                        savedHighlights: chapterHighlights,
                        highlightBarState: $highlightBarState,
                        defaultHighlightColorKey: appState.bibleHighlightStore.lastHighlightColorKey,
                        decryptNote: { appState.bibleHighlightStore.displayNote(for: $0) }
                    )
                } else {
                    Text("Chapter text is not loaded yet. Check your connection.")
                        .foregroundStyle(.secondary)
                }
            }
            .padding(MGTheme.standardPadding)
            .padding(.bottom, highlightBarState == nil ? 72 : 180)
        }
        .safeAreaInset(edge: .bottom) {
            if let barState = highlightBarState {
                VerseHighlightBottomBar(
                    state: Binding(
                        get: { barState },
                        set: { highlightBarState = $0 }
                    ),
                    onSave: saveHighlight,
                    onDelete: deleteHighlight,
                    onCopy: copySelection,
                    onDismiss: { highlightBarState = nil },
                    onColorSelected: { appState.bibleHighlightStore.setLastHighlightColorKey($0) }
                )
            } else {
                chapterNavBar
            }
        }
        .task(id: chapterLoadKey) { await loadChapter() }
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
        .alert("Saved to highlights", isPresented: $savedToast) {
            Button("OK", role: .cancel) {}
        }
    }

    private var chapterLoadKey: String {
        "\(config.bibleTranslation)|\(config.bibleBook)|\(config.bibleChapter)"
    }

    private var selectorRow: some View {
        HStack(spacing: 8) {
            Button {
                bookChapterSheet = true
            } label: {
                Label("\(config.bibleBook) \(config.bibleChapter)", systemImage: "book")
                    .lineLimit(1)
            }
            .buttonStyle(.bordered)

            Button {
                translationSheet = true
            } label: {
                Text(BibleTranslations.chipLabel(id: config.bibleTranslation))
            }
            .buttonStyle(.bordered)
        }
    }

    private var chapterNavBar: some View {
        VStack(spacing: 0) {
            Divider()
            HStack {
                Button {
                    appState.stepBibleChapter(delta: -1)
                } label: {
                    Image(systemName: "chevron.left")
                }
                .accessibilityLabel("Previous chapter")

                Spacer()
                Text("\(config.bibleBook) \(config.bibleChapter)")
                    .font(.subheadline.bold())
                    .lineLimit(1)
                    .onTapGesture { bookChapterSheet = true }

                Spacer()
                Button {
                    appState.stepBibleChapter(delta: 1)
                } label: {
                    Image(systemName: "chevron.right")
                }
                .accessibilityLabel("Next chapter")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.bar)
        }
    }

    private func loadChapter() async {
        loading = true
        errorMessage = nil
        highlightBarState = nil
        defer { loading = false }
        do {
            passage = try await appState.bibleClient.loadChapter(
                translation: config.bibleTranslation,
                book: config.bibleBook,
                chapter: config.bibleChapter
            )
        } catch {
            errorMessage = friendlyBibleError(error.localizedDescription)
        }
    }

    private func friendlyBibleError(_ error: String) -> String {
        if error.contains("-1011") || error.localizedCaseInsensitiveContains("badServerResponse") {
            return "Could not load chapter. Licensed translations use WEB on iOS until API.Bible is configured."
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
            )
        )
        highlightBarState = nil
        savedToast = true
    }

    private func deleteHighlight() {
        guard let existing = highlightBarState?.existingHighlight else { return }
        appState.bibleHighlightStore.deleteHighlight(id: existing.id)
        highlightBarState = nil
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
    }
}

#if canImport(UIKit)
import UIKit
#endif
