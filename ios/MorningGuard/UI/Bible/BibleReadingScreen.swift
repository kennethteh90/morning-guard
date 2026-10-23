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
                    Text("\(elapsedSeconds) / \(requiredSeconds) seconds on screen")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if !bibleReaderTipSeen {
                    bibleTipCard
                }

                if loading {
                    ProgressView("Loading chapter…")
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
                    Text("Chapter text is not loaded yet. Check your connection.")
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
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        translationSheet = true
                    } label: {
                        Text(BibleTranslations.chipLabel(id: config.bibleTranslation))
                            .font(.subheadline.weight(.semibold))
                    }
                }
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
                Text("Tap verses to highlight, then save from the bar below.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Button("Got it") { bibleReaderTipSeen = true }
                    .font(.subheadline.weight(.semibold))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MGTheme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var chapterLoadKey: String {
        "\(config.bibleTranslation)|\(config.bibleBook)|\(config.bibleChapter)"
    }

    private func bibleLoadErrorBanner(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(
                "Couldn't load \(config.bibleBook) \(config.bibleChapter) (\(BibleTranslations.chipLabel(id: config.bibleTranslation)))"
            )
            .font(.subheadline.bold())
            Text(message)
                .font(.subheadline)
            Button("Retry") { Task { await loadChapter() } }
                .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.red.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
    }

    private var chapterNavBar: some View {
        MGBottomAccessory {
            HStack(spacing: 4) {
                chapterNavCaretButton(
                    systemName: "chevron.left",
                    accessibilityLabel: "Previous chapter",
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
                } else {
                    Text(BibleTranslations.chipLabel(id: config.bibleTranslation))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                chapterNavCaretButton(
                    systemName: "chevron.right",
                    accessibilityLabel: "Next chapter",
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

    private func loadChapter() async {
        loading = true
        errorMessage = nil
        passage = nil
        withAnimation(.spring(duration: 0.35)) {
            highlightBarState = nil
        }
        defer { loading = false }
        do {
            passage = try await appState.bibleClient.loadChapter(
                translation: config.bibleTranslation,
                book: config.bibleBook,
                chapter: config.bibleChapter
            )
        } catch {
            passage = nil
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
        MGHaptics.success()
        withAnimation(.spring(duration: 0.35)) {
            highlightBarState = nil
        }
        MGBanner.present(.message("Saved to highlights"), binding: $banner)
    }

    private func deleteHighlight() {
        guard let existing = highlightBarState?.existingHighlight else { return }
        appState.bibleHighlightStore.deleteHighlight(id: existing.id)
        MGHaptics.lightImpact()
        withAnimation(.spring(duration: 0.35)) {
            highlightBarState = nil
        }
        MGBanner.present(.message("Highlight removed"), binding: $banner)
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
        MGBanner.present(.message("Copied to clipboard"), binding: $banner)
    }
}

#if canImport(UIKit)
import UIKit
#endif
