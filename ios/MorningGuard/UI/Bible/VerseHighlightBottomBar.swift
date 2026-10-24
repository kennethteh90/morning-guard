import SwiftUI
import MorningGuardCore

struct VerseHighlightBottomBar: View {
    @Binding var state: VerseHighlightBarState
    let onSave: () -> Void
    let onDelete: () -> Void
    let onCopy: () -> Void
    let onDismiss: () -> Void
    let onColorSelected: (String) -> Void

    @State private var noteExpanded = false

    private var isRemoveMode: Bool { state.existingHighlight != nil }

    var body: some View {
        MGBottomAccessory {
            VStack(spacing: 0) {
                headerRow
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, noteExpanded ? 8 : 10)

                if noteExpanded, !isRemoveMode {
                    TextField(L10n.tr("bible.highlight.note_field"), text: Binding(
                        get: { state.note },
                        set: { state.note = $0 }
                    ), axis: .vertical)
                    .lineLimit(2...5)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 10)
                }

                if !isRemoveMode {
                    actionRow
                        .padding(.horizontal, 12)
                        .padding(.bottom, 12)
                } else {
                    removeModeActions
                        .padding(.horizontal, 12)
                        .padding(.bottom, 12)
                }
            }
        }
        .onChange(of: state.selectedVerses) { _, verses in
            if verses.isEmpty { noteExpanded = false }
        }
    }

    private var headerRow: some View {
        HStack(spacing: 12) {
            if isRemoveMode, let saved = state.existingHighlight {
                Circle()
                    .fill(BibleHighlightColorsUi.color(for: saved.colorKey))
                    .frame(width: 28, height: 28)
                Text(saved.label.isEmpty ? L10n.tr("bible.highlight.verses_count_format", state.selectedVerses.count) : saved.label)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text(selectionCountLabel)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
                HStack(spacing: 8) {
                    ForEach(BibleHighlightColors.keys, id: \.self) { key in
                        colorSwatch(key: key)
                    }
                }
            }

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .frame(width: 32, height: 32)
                    .background(Color(.tertiarySystemFill), in: Circle())
            }
            .accessibilityLabel(L10n.tr("bible.highlight.clear_selection"))
            .accessibilityHint(L10n.tr("a11y.bible.highlight.dismiss"))
        }
    }

    private func colorSwatch(key: String) -> some View {
        Circle()
            .fill(BibleHighlightColorsUi.color(for: key))
            .frame(width: 28, height: 28)
            .overlay {
                if state.colorKey == key {
                    Circle().strokeBorder(MGTheme.accent, lineWidth: 2.5)
                }
            }
            .onTapGesture {
                MGHaptics.selection()
                onColorSelected(key)
                state.colorKey = key
            }
            .accessibilityLabel(L10n.tr("bible.highlight.color_format", key))
    }

    private var actionRow: some View {
        HStack(spacing: 8) {
            HighlightBarAction(
                label: L10n.tr("bible.highlight.save"),
                systemImage: "bookmark.fill",
                prominent: true,
                accessibilityLabel: L10n.tr("a11y.bible.highlight.save"),
                action: onSave
            )
            HighlightBarAction(
                label: noteExpanded ? L10n.tr("bible.highlight.hide_note") : L10n.tr("bible.highlight.note"),
                systemImage: "square.and.pencil",
                prominent: false,
                accessibilityLabel: L10n.tr("a11y.bible.highlight.note"),
                action: { noteExpanded.toggle() }
            )
            HighlightBarAction(
                label: L10n.tr("bible.highlight.copy"),
                systemImage: "doc.on.doc",
                prominent: false,
                accessibilityLabel: L10n.tr("a11y.bible.highlight.copy"),
                action: onCopy
            )
        }
    }

    private var removeModeActions: some View {
        HStack(spacing: 8) {
            HighlightBarAction(
                label: L10n.tr("bible.highlight.delete"),
                systemImage: "trash",
                prominent: false,
                destructive: true,
                accessibilityLabel: L10n.tr("a11y.bible.highlight.delete"),
                action: onDelete
            )
            HighlightBarAction(
                label: L10n.tr("bible.highlight.copy"),
                systemImage: "doc.on.doc",
                prominent: false,
                accessibilityLabel: L10n.tr("a11y.bible.highlight.copy"),
                action: onCopy
            )
        }
    }

    private var selectionCountLabel: String {
        let count = state.selectedVerses.count
        if count == 0 { return L10n.tr("bible.highlight.select_verses") }
        if count == 1 { return L10n.tr("bible.highlight.one_verse_selected") }
        return L10n.tr("bible.highlight.verses_selected_format", count)
    }
}

private struct HighlightBarAction: View {
    let label: String
    let systemImage: String
    var prominent: Bool = false
    var destructive: Bool = false
    var accessibilityLabel: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(label, systemImage: systemImage)
                .font(.subheadline.weight(.medium))
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .foregroundStyle(foregroundColor)
        .background(backgroundColor, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityLabel(accessibilityLabel ?? label)
    }

    private var foregroundColor: Color {
        if destructive { return .red }
        if prominent { return .white }
        return .primary
    }

    private var backgroundColor: Color {
        if destructive { return Color.red.opacity(0.12) }
        if prominent { return MGTheme.accent }
        return Color(.tertiarySystemFill)
    }
}
