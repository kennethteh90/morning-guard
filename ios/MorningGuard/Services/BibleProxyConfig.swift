import Foundation
import MorningGuardCore

enum BibleProxyConfig {
    static var baseURL: String {
        (Bundle.main.object(forInfoDictionaryKey: "BIBLE_PROXY_BASE_URL") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    static var isConfigured: Bool {
        !baseURL.isEmpty
    }

    static var missingPieceMessage: String? {
        if baseURL.isEmpty {
            return "Set BIBLE_PROXY_BASE_URL in ios/Config/Local.xcconfig."
        }
        return nil
    }

    static func isLicensedTranslation(_ translationId: String) -> Bool {
        BibleTranslations.find(id: translationId)?.source == .apiBible
    }
}
