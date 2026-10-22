import SwiftUI
import MorningGuardCore

struct VerseHighlightBottomBar: View {
    @Binding var state: VerseHighlightBarState
    let onSave: () -> Void
    let onDelete: () -> Void
    let onCopy: () -> Void
    let onDismiss: () -> Void
    let onColorSelected: (String) -> Void

    @State private var noteEditorOpen = false

    var body: some View {
        VStack(spacing: 0) {
            Divider()
            HStack {
                Button("Close", action: onDismiss)
                Spacer()
                Text(selectionLabel)
                    .font(.subheadline.bold())
                Spacer()
                Button(action: onCopy) {
                    Image(systemName: "doc.on.doc")
                }
                .accessibilityLabel("Copy verses")
            }
            .padding(.horizontal)
            .padding(.vertical, 8)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(BibleHighlightColors.keys, id: \.self) { key in
                        Circle()
                            .fill(BibleHighlightColorsUi.color(for: key))
                            .frame(width: 32, height: 32)
                            .overlay {
                                if state.colorKey == key {
                                    Circle().stroke(MGTheme.accent, lineWidth: 2)
                                }
                            }
                            .onTapGesture {
                                onColorSelected(key)
                                state.colorKey = key
                            }
                    }
                }
                .padding(.horizontal)
            }
            .padding(.bottom, 8)

            HStack(spacing: 12) {
                Button {
                    noteEditorOpen = true
                } label: {
                    Label(state.note.isEmpty ? "Add note" : "Edit note", systemImage: "square.and.pencil")
                }
                .buttonStyle(.bordered)

                if state.existingHighlight != nil {
                    Button("Delete", role: .destructive, action: onDelete)
                        .buttonStyle(.bordered)
                } else {
                    Button("Save", action: onSave)
                        .buttonStyle(.borderedProminent)
                        .tint(MGTheme.accent)
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 8)
        }
        .background(.bar)
        .sheet(isPresented: $noteEditorOpen) {
            NavigationStack {
                Form {
                    TextField("Note", text: Binding(
                        get: { state.note },
                        set: { state.note = $0 }
                    ), axis: .vertical)
                    .lineLimit(3...8)
                }
                .navigationTitle("Highlight note")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { noteEditorOpen = false }
                    }
                }
            }
            .presentationDetents([.medium])
        }
    }

    private var selectionLabel: String {
        let sorted = state.selectedVerses.sorted()
        if sorted.isEmpty { return "Select verses" }
        if sorted.count == 1 { return "Verse \(sorted[0])" }
        return "Verses \(sorted.map(String.init).joined(separator: ", "))"
    }
}
