import SwiftUI
import MorningGuardCore

struct HighlightsView: View {
    @Environment(AppState.self) private var appState
    @State private var search = ""
    @State private var banner: MGBannerPayload?
    @State private var pendingUndoHighlight: BibleHighlight?
    @State private var openReader = false

    private var filtered: [BibleHighlight] {
        let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
        if query.isEmpty { return appState.bibleHighlightStore.highlights }
        return appState.bibleHighlightStore.highlights.filter {
            $0.label.localizedCaseInsensitiveContains(query)
                || $0.excerpt.localizedCaseInsensitiveContains(query)
                || $0.note.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        List {
            RecoveryPasswordBannerView()
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            if filtered.isEmpty {
                ContentUnavailableView(
                    search.isEmpty ? "No highlights yet" : "No matches",
                    systemImage: "bookmark",
                    description: Text(search.isEmpty
                        ? "Highlight verses while reading your morning chapter or in Read Bible."
                        : "Try a different search.")
                )
            } else {
                ForEach(filtered) { highlight in
                    HighlightRow(
                        highlight: highlight,
                        note: appState.bibleHighlightStore.displayNote(for: highlight)
                    )
                    .contentShape(Rectangle())
                    .onTapGesture {
                        openPassage(for: highlight)
                    }
                    .swipeActions(edge: .trailing) {
                        Button("Delete", role: .destructive) {
                            deleteWithUndo(highlight)
                        }
                        ShareLink(item: BibleVerseCopyFormat.shareText(highlight: highlight)) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                    }
                }
            }
        }
        .navigationTitle("Saved highlights")
        .searchable(text: $search, prompt: "Search highlights")
        .overlay(alignment: .top) {
            MGBannerOverlay(payload: banner, onUndo: undoDelete)
        }
        .navigationDestination(isPresented: $openReader) {
            BibleReaderView()
        }
    }

    private func openPassage(for highlight: BibleHighlight) {
        appState.setBibleTranslation(highlight.translationId)
        appState.setBibleReading(book: highlight.book, chapter: highlight.chapter)
        openReader = true
    }

    private func deleteWithUndo(_ highlight: BibleHighlight) {
        guard let removed = appState.bibleHighlightStore.deleteHighlightReturningRemoved(id: highlight.id) else { return }
        pendingUndoHighlight = removed
        MGBanner.present(.undo("Highlight removed"), binding: $banner, duration: 5) {
            Task { @MainActor in
                guard pendingUndoHighlight?.id == removed.id else { return }
                pendingUndoHighlight = nil
                await appState.userDataSync.deleteHighlight(removed)
            }
        }
    }

    private func undoDelete() {
        guard let highlight = pendingUndoHighlight else { return }
        appState.bibleHighlightStore.restoreHighlight(highlight)
        pendingUndoHighlight = nil
        banner = nil
        MGHaptics.lightImpact()
    }
}

private struct HighlightRow: View {
    let highlight: BibleHighlight
    let note: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(BibleHighlightColorsUi.color(for: highlight.colorKey))
                .frame(width: 4)
                .padding(.vertical, 2)

            VStack(alignment: .leading, spacing: 8) {
                Text(BibleVerseCopyFormat.referenceLine(highlight: highlight))
                    .font(.headline)
                Text(BibleVerseCopyFormat.bodyFromHighlight(highlight: highlight))
                    .font(.body)
                    .foregroundStyle(.primary)
                    .lineLimit(4)
                if !note.isEmpty {
                    Text(note)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        }
        .padding(.vertical, 4)
    }
}
