import Foundation
import MorningGuardCore

struct BibleClient: Sendable {
    private let openBible: OpenBibleClient
    private let proxy: BibleProxyClient

    init(session: URLSession = .shared, baseURL: URL? = nil) {
        openBible = OpenBibleClient(session: session, baseURL: baseURL)
        proxy = BibleProxyClient(session: session)
    }

    func loadChapter(translation: String, book: String, chapter: Int) async throws -> BibleChapterPassage {
        let fallbackReference = "\(book) \(chapter)"
        guard let option = BibleTranslations.find(id: translation) else {
            return try await openBible.loadChapter(
                translation: BibleTranslations.defaultId,
                book: book,
                chapter: chapter
            )
        }

        switch option.source {
        case .apiBible:
            return try await loadLicensedViaProxy(
                translation: BibleTranslations.normalizeId(translation),
                book: book,
                chapter: chapter,
                label: option.chipLabel
            )
        case .openBible:
            if BibleProxyConfig.isConfigured {
                do {
                    let viaProxy = try await proxy.loadChapter(
                        translation: BibleTranslations.normalizeId(translation),
                        book: book,
                        chapter: chapter
                    )
                    if !Self.paragraphsLackVerseMarkers(viaProxy.blocks) {
                        return viaProxy
                    }
                } catch {
                    // Fall through to bible-api.com.
                }
            }
            return try await openBible.loadChapter(
                translation: BibleTranslations.normalizeId(translation),
                book: book,
                chapter: chapter
            )
        }
    }

    private func loadLicensedViaProxy(
        translation: String,
        book: String,
        chapter: Int,
        label: String
    ) async throws -> BibleChapterPassage {
        if let hint = BibleProxyConfig.missingPieceMessage {
            throw NSError(
                domain: "BibleClient",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey:
                    "\(hint) Licensed translations (\(label)) load through the Morning Guard Bible proxy."]
            )
        }
        return try await proxy.loadChapter(translation: translation, book: book, chapter: chapter)
    }

    private static func paragraphsLackVerseMarkers(_ blocks: [BiblePassageBlock]) -> Bool {
        let text = blocks.compactMap { block -> String? in
            if case .paragraph(let value) = block { return value }
            return nil
        }.joined(separator: "\n")
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
        return text.range(of: #"(\[\d+\]|\d+\.)"#, options: .regularExpression) == nil
    }
}

private struct OpenBibleClient: Sendable {
    private let session: URLSession
    private let baseURL: URL

    init(session: URLSession, baseURL: URL?) {
        self.session = session
        if let baseURL {
            self.baseURL = baseURL
        } else if let configured = Bundle.main.object(forInfoDictionaryKey: "BIBLE_API_BASE_URL") as? String,
                  let url = URL(string: configured) {
            self.baseURL = url
        } else {
            self.baseURL = URL(string: "https://bible-api.com/")!
        }
    }

    func loadChapter(translation: String, book: String, chapter: Int) async throws -> BibleChapterPassage {
        let openTranslation = Self.openBibleTranslationId(for: translation)
        do {
            return try await fetchChapter(book: book, chapter: chapter, translation: openTranslation)
        } catch let error as URLError where error.code == .badServerResponse && openTranslation != nil {
            return try await fetchChapter(book: book, chapter: chapter, translation: nil)
        }
    }

    private static func openBibleTranslationId(for translation: String) -> String? {
        let id = translation.lowercased()
        switch id {
        case "niv", "nkjv", "nlt", "esv":
            return nil
        case "web", "":
            return nil
        default:
            return id
        }
    }

    private func fetchChapter(book: String, chapter: Int, translation: String?) async throws -> BibleChapterPassage {
        let bookSlug = book.lowercased().replacingOccurrences(of: " ", with: "+")
        let path = "\(bookSlug)+\(chapter)"
        var base = baseURL.absoluteString
        if base.hasSuffix("/") { base.removeLast() }
        var components = URLComponents(string: "\(base)/\(path)")
        if let translation, translation.lowercased() != "web" {
            components?.queryItems = [URLQueryItem(name: "translation", value: translation.lowercased())]
        }
        guard let url = components?.url else { throw URLError(.badURL) }

        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoded = try JSONDecoder().decode(OpenBibleResponse.self, from: data)
        let fallbackReference = "\(book) \(chapter)"
        let body: String
        if !decoded.verses.isEmpty {
            body = decoded.verses.map { verse in
                let text = verse.text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\n", with: " ")
                return "\(verse.verse). \(text)"
            }.joined(separator: "\n")
        } else if let text = decoded.text?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
            body = text
        } else {
            body = "(content unavailable offline)"
        }
        let reference = decoded.reference.isEmpty ? fallbackReference : decoded.reference
        return BiblePassageFormatter.parseChapterBody(
            body: "\(reference)\n\n\(body)",
            fallbackReference: fallbackReference
        )
    }
}

private struct OpenBibleResponse: Decodable {
    var reference: String
    var text: String?
    var verses: [OpenBibleVerse]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        reference = try container.decodeIfPresent(String.self, forKey: .reference) ?? ""
        text = try container.decodeIfPresent(String.self, forKey: .text)
        verses = try container.decodeIfPresent([OpenBibleVerse].self, forKey: .verses) ?? []
    }

    enum CodingKeys: String, CodingKey {
        case reference, text, verses
    }
}

private struct OpenBibleVerse: Decodable {
    var verse: Int
    var text: String
}
