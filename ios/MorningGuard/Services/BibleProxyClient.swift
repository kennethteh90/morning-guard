import CryptoKit
import DeviceCheck
import Foundation
import MorningGuardCore

struct BibleProxyClient: Sendable {
    private let session: URLSession
    private let licensedAuth: BibleProxyLicensedAuth

    init(session: URLSession = .shared, licensedAuth: BibleProxyLicensedAuth = AppAttestBibleProxyLicensedAuth()) {
        self.session = session
        self.licensedAuth = licensedAuth
    }

    func loadChapter(translation: String, book: String, chapter: Int) async throws -> BibleChapterPassage {
        guard BibleProxyConfig.isConfigured else {
            throw BibleProxyError.notConfigured
        }
        let normalized = BibleTranslations.normalizeId(translation)
        var components = URLComponents(string: "\(BibleProxyConfig.baseURL)/v1/chapter")!
        components.queryItems = [
            URLQueryItem(name: "translation", value: normalized),
            URLQueryItem(name: "book", value: book),
            URLQueryItem(name: "chapter", value: String(chapter)),
        ]
        guard let url = components.url else { throw URLError(.badURL) }

        var request = URLRequest(url: url)
        if BibleProxyConfig.isLicensedTranslation(normalized) {
            try await licensedAuth.applyLicensedHeaders(to: &request)
        }

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        guard (200...299).contains(http.statusCode) else {
            throw BibleProxyError.http(status: http.statusCode, body: String(data: data, encoding: .utf8) ?? "")
        }

        let decoded = try JSONDecoder().decode(BibleProxyChapterResponse.self, from: data)
        let fallbackReference = "\(book) \(chapter)"
        let blocks = mapBlocks(decoded)
        if blocks.isEmpty {
            let parsed = BiblePassageFormatter.parseChapterBody(
                body: decoded.body,
                fallbackReference: fallbackReference
            )
            if parsed.blocks.isEmpty {
                throw BibleProxyError.emptyChapter
            }
            return parsed
        }
        let reference = decoded.reference.isEmpty ? fallbackReference : decoded.reference
        return BibleChapterPassage(reference: reference, blocks: blocks)
    }

    private func mapBlocks(_ response: BibleProxyChapterResponse) -> [BiblePassageBlock] {
        guard let dtos = response.blocks else { return [] }
        return dtos.compactMap { dto -> BiblePassageBlock? in
            let text = dto.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else { return nil }
            switch dto.type.lowercased() {
            case "heading": return .sectionHeading(text)
            case "paragraph": return .paragraph(text)
            default: return nil
            }
        }
    }
}

protocol BibleProxyLicensedAuth: Sendable {
    func applyLicensedHeaders(to request: inout URLRequest) async throws
}

struct AppAttestBibleProxyLicensedAuth: BibleProxyLicensedAuth, Sendable {
    private let service = DCAppAttestService.shared
    private let keyIdDefaultsKey = "bibleProxyAppAttestKeyId"
    private let attestedDefaultsKey = "bibleProxyAppAttestRegistered"

    func applyLicensedHeaders(to request: inout URLRequest) async throws {
        guard service.isSupported else {
            throw BibleProxyError.appAttestUnavailable
        }
        let nonce = try await fetchChallengeNonce()
        request.setValue(nonce, forHTTPHeaderField: "X-Morning-Guard-Nonce")

        let keyId = try await ensureKeyId()
        let clientData = Data(nonce.utf8)
        let hash = Data(SHA256.hash(data: clientData))
        request.setValue(keyId, forHTTPHeaderField: "X-Morning-Guard-App-Attest-Key-Id")
        if !UserDefaults.standard.bool(forKey: attestedDefaultsKey) {
            let attestation = try await attestKey(keyId: keyId, clientDataHash: hash)
            request.setValue(attestation.base64EncodedString(), forHTTPHeaderField: "X-Morning-Guard-App-Attest-Attestation")
            UserDefaults.standard.set(true, forKey: attestedDefaultsKey)
        } else {
            let assertion = try await generateAssertion(keyId: keyId, clientDataHash: hash)
            request.setValue(assertion.base64EncodedString(), forHTTPHeaderField: "X-Morning-Guard-App-Attest-Assertion")
        }
    }

    private func fetchChallengeNonce() async throws -> String {
        guard let challengeURL = URL(string: "\(BibleProxyConfig.baseURL)/v1/challenge") else {
            throw URLError(.badURL)
        }
        var challengeRequest = URLRequest(url: challengeURL)
        challengeRequest.httpMethod = "POST"
        let (data, response) = try await URLSession.shared.data(for: challengeRequest)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw BibleProxyError.http(status: (response as? HTTPURLResponse)?.statusCode ?? -1, body: "")
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard let nonce = json?["nonce"] as? String, !nonce.isEmpty else {
            throw URLError(.badServerResponse)
        }
        return nonce
    }

    private func ensureKeyId() async throws -> String {
        if let existing = UserDefaults.standard.string(forKey: keyIdDefaultsKey), !existing.isEmpty {
            return existing
        }
        let keyId: String = try await withCheckedThrowingContinuation { continuation in
            service.generateKey { keyId, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let keyId {
                    continuation.resume(returning: keyId)
                } else {
                    continuation.resume(throwing: URLError(.unknown))
                }
            }
        }
        UserDefaults.standard.set(keyId, forKey: keyIdDefaultsKey)
        return keyId
    }

    private func attestKey(keyId: String, clientDataHash: Data) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            service.attestKey(keyId, clientDataHash: clientDataHash) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: URLError(.unknown))
                }
            }
        }
    }

    private func generateAssertion(keyId: String, clientDataHash: Data) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            service.generateAssertion(keyId, clientDataHash: clientDataHash) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: URLError(.unknown))
                }
            }
        }
    }
}

enum BibleProxyError: LocalizedError {
    case notConfigured
    case emptyChapter
    case appAttestUnavailable
    case http(status: Int, body: String)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return BibleProxyConfig.missingPieceMessage ?? "Bible proxy is not configured."
        case .emptyChapter:
            return "Empty chapter from Bible proxy."
        case .appAttestUnavailable:
            return "Licensed translations require a physical device with App Attest."
        case .http(let status, let body):
            if let message = Self.parseErrorMessage(body) {
                if status == 403, message.localizedCaseInsensitiveContains("Licensed") {
                    return "\(message) Switch to WEB or KJV, or enable licensed translations on the worker."
                }
                return message
            }
            return "Bible server error (HTTP \(status))."
        }
    }

    private static func parseErrorMessage(_ body: String) -> String? {
        guard let data = body.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let error = json["error"] as? String,
              !error.isEmpty else { return nil }
        return error
    }
}

private struct BibleProxyChapterResponse: Decodable {
    var reference: String
    var body: String
    var blocks: [BibleProxyBlockDto]?

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        reference = try container.decodeIfPresent(String.self, forKey: .reference) ?? ""
        body = try container.decodeIfPresent(String.self, forKey: .body) ?? ""
        blocks = try container.decodeIfPresent([BibleProxyBlockDto].self, forKey: .blocks)
    }

    enum CodingKeys: String, CodingKey {
        case reference, body, blocks
    }
}

private struct BibleProxyBlockDto: Decodable {
    var type: String
    var text: String
}
