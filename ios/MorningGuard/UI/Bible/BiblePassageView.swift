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
                        highlightBackground: verseBackground(saved: saved, isSelected: isSelected),
                        showSelection: isSelected
                    ) {
                        guard verseSelectionEnabled else { return }
                        MGHaptics.selection()
                        if isSelected {
                            var selected = highlightBarState?.selectedVerses ?? []
                            selected.remove(number)
                            if selected.isEmpty {
                                withAnimation(.spring(duration: 0.35)) {
                                    highlightBarState = nil
                                }
                            } else {
                                withAnimation(.spring(duration: 0.35)) {
                                    highlightBarState = VerseHighlightBarState(
                                        selectedVerses: selected,
                                        verseSnippets: verseSnippets,
                                        colorKey: highlightBarState?.colorKey ?? defaultHighlightColorKey,
                                        note: highlightBarState?.note ?? "",
                                        existingHighlight: highlightBarState?.existingHighlight
                                    )
                                }
                            }
                            return
                        }
                        if let saved {
                            let verses = BibleHighlightVerses.numbers(highlight: saved)
                            withAnimation(.spring(duration: 0.35)) {
                                highlightBarState = VerseHighlightBarState(
                                    selectedVerses: verses,
                                    verseSnippets: verseSnippets,
                                    colorKey: saved.colorKey,
                                    note: decryptNote(saved),
                                    existingHighlight: saved
                                )
                            }
                            return
                        }
                        var selected = highlightBarState?.selectedVerses ?? []
                        selected.insert(number)
                        withAnimation(.spring(duration: 0.35)) {
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

    private func verseBackground(saved: BibleHighlight?, isSelected: Bool) -> Color? {
        if isSelected {
            return MGTheme.accent.opacity(0.14)
        }
        if let saved {
            return BibleHighlightColorsUi.color(for: saved.colorKey)
                .opacity(BibleHighlightColorsUi.savedVerseHighlightOpacity)
        }
        return nil
    }
}

private struct VerseLineView: View {
    let verseNumber: Int
    let text: String
    let highlightBackground: Color?
    let showSelection: Bool
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
                    .lineSpacing(4)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 6)
            .padding(.horizontal, 8)
            .background {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(highlightBackground ?? .clear)
            }
            .overlay(alignment: .trailing) {
                if showSelection {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.caption)
                        .foregroundStyle(MGTheme.accent)
                        .padding(.trailing, 4)
                }
            }
        }
        .buttonStyle(.plain)
    }
}
