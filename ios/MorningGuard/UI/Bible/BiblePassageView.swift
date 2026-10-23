import SwiftUI
import MorningGuardCore

struct VerseSnippet: Equatable {
    var number: Int
    var text: String
}

struct VerseHighlightBarState: Equatable {
    var selectedVerses: Set<Int>
    var verseSnippets: [VerseSnippet]
    var colorKey: String
    var note: String
    var existingHighlight: BibleHighlight?
}

struct BiblePassageView: View {
    let blocks: [BiblePassageBlock]
    let savedHighlights: [BibleHighlight]
    @Binding var highlightBarState: VerseHighlightBarState?
    var defaultHighlightColorKey: String = BibleHighlightColors.defaultKey
    var verseSelectionEnabled: Bool = true
    var decryptNote: (BibleHighlight) -> String = { $0.note }

    private var segments: [BiblePassageVerseParser.Segment] {
        BiblePassageVerseParser.segments(blocks: blocks)
    }

    private var highlightsByVerse: [Int: BibleHighlight] {
        var map: [Int: BibleHighlight] = [:]
        for highlight in savedHighlights {
            for number in BibleHighlightVerses.numbers(highlight: highlight) {
                map[number] = highlight
            }
        }
        return map
    }

    var body: some View {
        let verseSnippets = segments.compactMap { segment -> VerseSnippet? in
            if case .verse(let number, let text) = segment {
                return VerseSnippet(number: number, text: text)
            }
            return nil
        }

        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                switch segment {
                case .heading(let text):
                    Text(text)
                        .font(.headline)
                        .padding(.top, 8)
                case .verse(let number, let text):
                    let saved = highlightsByVerse[number]
                    let isSelected = highlightBarState?.selectedVerses.contains(number) ?? false
                    VerseLineView(
                        verseNumber: number,
                        text: text,
                        highlightBackground: saved != nil && !isSelected
                            ? BibleHighlightColorsUi.color(for: saved!.colorKey).opacity(BibleHighlightColorsUi.savedVerseHighlightOpacity)
                            : nil,
                        showSelectionUnderline: isSelected
                    ) {
                        guard verseSelectionEnabled else { return }
                        if let saved {
                            let verses = BibleHighlightVerses.numbers(highlight: saved)
                            highlightBarState = VerseHighlightBarState(
                                selectedVerses: verses,
                                verseSnippets: verseSnippets,
                                colorKey: saved.colorKey,
                                note: decryptNote(saved),
                                existingHighlight: saved
                            )
                            return
                        }
                        var selected = highlightBarState?.selectedVerses ?? []
                        if isSelected {
                            selected.remove(number)
                        } else {
                            selected.insert(number)
                        }
                        if selected.isEmpty {
                            highlightBarState = nil
                        } else {
                            highlightBarState = VerseHighlightBarState(
                                selectedVerses: selected,
                                verseSnippets: verseSnippets,
                                colorKey: highlightBarState?.colorKey ?? defaultHighlightColorKey,
                                note: highlightBarState?.note ?? "",
                                existingHighlight: nil
                            )
                        }
                    }
                }
            }
        }
        .onChange(of: blocks) { _, _ in
            highlightBarState = nil
        }
    }
}

private struct VerseLineView: View {
    let verseNumber: Int
    let text: String
    let highlightBackground: Color?
    let showSelectionUnderline: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("\(verseNumber)")
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
                    .frame(minWidth: 22, alignment: .trailing)
                Text(text)
                    .font(.body)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 4)
            .padding(.horizontal, 6)
            .background(highlightBackground ?? .clear)
            .overlay(alignment: .bottom) {
                if showSelectionUnderline {
                    Rectangle()
                        .fill(MGTheme.accent)
                        .frame(height: 2)
                }
            }
        }
        .buttonStyle(.plain)
    }
}
