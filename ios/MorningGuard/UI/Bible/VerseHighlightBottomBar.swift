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
                    TextField("Note for this highlight", text: Binding(
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
                Text(saved.label.isEmpty ? "\(state.selectedVerses.count) verses" : saved.label)
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
            .accessibilityLabel("Clear selection")
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
            .accessibilityLabel("Highlight color \(key)")
    }

    private var actionRow: some View {
        HStack(spacing: 8) {
            HighlightBarAction(label: "Save", systemImage: "bookmark.fill", prominent: true, action: onSave)
            HighlightBarAction(
                label: noteExpanded ? "Hide note" : "Note",
                systemImage: "square.and.pencil",
                prominent: false,
                action: { noteExpanded.toggle() }
            )
            HighlightBarAction(label: "Copy", systemImage: "doc.on.doc", prominent: false, action: onCopy)
        }
    }

    private var removeModeActions: some View {
        HStack(spacing: 8) {
            HighlightBarAction(label: "Delete", systemImage: "trash", prominent: false, destructive: true, action: onDelete)
            HighlightBarAction(label: "Copy", systemImage: "doc.on.doc", prominent: false, action: onCopy)
        }
    }

    private var selectionCountLabel: String {
        let count = state.selectedVerses.count
        if count == 0 { return "Select verses" }
        if count == 1 { return "1 verse selected" }
        return "\(count) verses selected"
    }
}

private struct HighlightBarAction: View {
    let label: String
    let systemImage: String
    var prominent: Bool = false
    var destructive: Bool = false
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
