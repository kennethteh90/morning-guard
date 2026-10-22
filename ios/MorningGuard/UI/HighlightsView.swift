import SwiftUI
import MorningGuardCore

struct HighlightsView: View {
    @Environment(AppState.self) private var appState
    @State private var search = ""
    @State private var pendingDelete: BibleHighlight?

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
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                pendingDelete = highlight
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
        .confirmationDialog(
            "Remove highlight?",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let highlight = pendingDelete {
                    appState.bibleHighlightStore.deleteHighlight(id: highlight.id)
                }
                pendingDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
    }
}

private struct HighlightRow: View {
    let highlight: BibleHighlight
    let note: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Circle()
                    .fill(BibleHighlightColorsUi.color(for: highlight.colorKey))
                    .frame(width: 12, height: 12)
                Text(BibleVerseCopyFormat.referenceLine(highlight: highlight))
                    .font(.headline)
            }
            Text(BibleVerseCopyFormat.bodyFromHighlight(highlight: highlight))
                .font(.body)
                .foregroundStyle(.primary)
            if !note.isEmpty {
                Text(note)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
