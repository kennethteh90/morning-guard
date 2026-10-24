import SwiftUI
import MorningGuardCore

struct BibleTranslationPickerSheet: View {
    let selectedTranslationId: String
    let onSelect: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section(L10n.tr("bible.translation.limited_section")) {
                    ForEach(BibleTranslations.apiBibleOptions(), id: \.id) { option in
                        translationRow(option)
                    }
                }
                Section(L10n.tr("bible.translation.open_section")) {
                    ForEach(BibleTranslations.openBibleOptions(), id: \.id) { option in
                        translationRow(option)
                    }
                }
            }
            .navigationTitle(L10n.tr("bible.translation.title"))
            .navigationBarTitleDisplayMode(.inline)
            .accessibilityLabel(L10n.tr("a11y.bible.translation_picker"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr("common.done")) { dismiss() }
                }
            }
        }
    }

    @ViewBuilder
    private func translationRow(_ option: BibleTranslationOption) -> some View {
        let selected = option.id.caseInsensitiveCompare(selectedTranslationId) == .orderedSame
        Button {
            onSelect(option.id)
            dismiss()
        } label: {
            HStack {
                Text(option.chipLabel)
                    .font(.headline)
                    .frame(width: 56, alignment: .leading)
                VStack(alignment: .leading) {
                    Text(option.fullName)
                    if option.source == .apiBible {
                        Text(L10n.tr("bible.translation.web_fallback"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                if selected {
                    Image(systemName: "checkmark")
                        .foregroundStyle(MGTheme.accent)
                }
            }
        }
        .accessibilityLabel(L10n.tr("a11y.bible.translation_row"))
        .accessibilityValue(option.fullName)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct BibleBookChapterPickerSheet: View {
    let selectedBook: String
    let selectedChapter: Int
    let onSelect: (String, Int) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var draftBook: String
    @State private var draftChapter: Int

    private let panelHeight: CGFloat = 300

    init(selectedBook: String, selectedChapter: Int, onSelect: @escaping (String, Int) -> Void) {
        self.selectedBook = selectedBook
        self.selectedChapter = selectedChapter
        self.onSelect = onSelect
        _draftBook = State(initialValue: BibleCanon.normalizeBook(bookName: selectedBook))
        _draftChapter = State(initialValue: selectedChapter)
    }

    private var chapterCount: Int {
        BibleCanon.chapterCount(bookName: draftBook)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text(L10n.tr("bible.book_chapter.hint"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 4)

                HStack(alignment: .top, spacing: 10) {
                    bookPanel
                    chapterPanel
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)
            .navigationTitle(L10n.tr("bible.book_chapter.title"))
            .navigationBarTitleDisplayMode(.inline)
            .accessibilityLabel(L10n.tr("a11y.bible.book_picker"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr("common.cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.tr("common.apply")) {
                        onSelect(draftBook, draftChapter)
                        dismiss()
                    }
                }
            }
            .onChange(of: draftBook) { _, newBook in
                let maxChapter = BibleCanon.chapterCount(bookName: newBook)
                if draftChapter > maxChapter {
                    draftChapter = maxChapter
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var bookPanel: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.tr("bible.book"))
                .font(.subheadline.bold())
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(BibleCanon.books, id: \.name) { book in
                            let selected = book.name == draftBook
                            Button {
                                draftBook = book.name
                                draftChapter = min(draftChapter, book.chapterCount)
                            } label: {
                                HStack {
                                    Text(book.name)
                                        .font(.subheadline)
                                        .foregroundStyle(selected ? MGTheme.accent : .primary)
                                    Spacer(minLength: 0)
                                    if selected {
                                        Image(systemName: "checkmark")
                                            .font(.caption.bold())
                                            .foregroundStyle(MGTheme.accent)
                                    }
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 10)
                                .background(selected ? MGTheme.accent.opacity(0.12) : Color.clear)
                            }
                            .buttonStyle(.plain)
                            .id(book.name)
                            .accessibilityLabel(L10n.tr("a11y.bible.book_row"))
                            .accessibilityValue(book.name)
                            .accessibilityAddTraits(selected ? .isSelected : [])
                            Divider()
                        }
                    }
                }
                .onAppear {
                    scrollToBook(proxy, animated: false)
                }
                .onChange(of: draftBook) { _, _ in
                    scrollToBook(proxy, animated: true)
                }
            }
            .frame(height: panelHeight)
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color(.separator).opacity(0.35))
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var chapterPanel: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.tr("bible.chapter"))
                .font(.subheadline.bold())
            Text(draftBook)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(2)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 44), spacing: 8)],
                        spacing: 8
                    ) {
                        ForEach(1...chapterCount, id: \.self) { chapter in
                            let selected = chapter == draftChapter
                            Button {
                                draftChapter = chapter
                            } label: {
                                Text("\(chapter)")
                                    .font(.subheadline.monospacedDigit())
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(selected ? MGTheme.accent : Color(.tertiarySystemFill))
                                    .foregroundStyle(selected ? Color.white : .primary)
                                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .id(chapter)
                            .accessibilityLabel(L10n.tr("a11y.bible.chapter_row"))
                            .accessibilityValue("\(chapter)")
                            .accessibilityAddTraits(selected ? .isSelected : [])
                        }
                    }
                    .padding(8)
                }
                .onAppear {
                    scrollToChapter(proxy, animated: false)
                }
                .onChange(of: draftChapter) { _, _ in
                    scrollToChapter(proxy, animated: true)
                }
                .onChange(of: draftBook) { _, _ in
                    scrollToChapter(proxy, animated: true)
                }
            }
            .frame(height: panelHeight - 20)
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color(.separator).opacity(0.35))
            }
        }
        .frame(width: 148)
    }

    private func scrollToBook(_ proxy: ScrollViewProxy, animated: Bool) {
        if animated {
            withAnimation { proxy.scrollTo(draftBook, anchor: .center) }
        } else {
            proxy.scrollTo(draftBook, anchor: .center)
        }
    }

    private func scrollToChapter(_ proxy: ScrollViewProxy, animated: Bool) {
        if animated {
            withAnimation { proxy.scrollTo(draftChapter, anchor: .center) }
        } else {
            proxy.scrollTo(draftChapter, anchor: .center)
        }
    }
}
