import Foundation

struct BibleChapterContent: Equatable, Sendable {
    var reference: String
    var plainText: String
    var paragraphs: [String]
}

struct BibleClient: Sendable {
    private let session: URLSession
    private let baseURL: URL

    init(session: URLSession = .shared, baseURL: URL? = nil) {
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

    func loadChapter(translation: String, book: String, chapter: Int) async throws -> BibleChapterContent {
        let bookSlug = book.lowercased().replacingOccurrences(of: " ", with: "+")
        let path = "\(bookSlug)+\(chapter)"
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)
        if translation.lowercased() != "web" {
            components?.queryItems = [URLQueryItem(name: "translation", value: translation.lowercased())]
        }
        guard let url = components?.url else {
            throw URLError(.badURL)
        }

        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoded = try JSONDecoder().decode(OpenBibleResponse.self, from: data)
        let fallbackReference = "\(book) \(chapter)"
        let reference = decoded.reference.isEmpty ? fallbackReference : decoded.reference
        let body: String
        if let text = decoded.text?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
            body = text
        } else {
            body = decoded.verses.map { "\($0.verse). \($0.text.trimmingCharacters(in: .whitespacesAndNewlines))" }
                .joined(separator: "\n")
        }
        let fullText = body.isEmpty ? "(content unavailable offline)" : body
        let paragraphs = fullText
            .components(separatedBy: "\n\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return BibleChapterContent(reference: reference, plainText: fullText, paragraphs: paragraphs)
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
