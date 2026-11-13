import Foundation

/// When licensed (API.Bible) chapters fail, the reader can switch to open translations without backend changes.
public enum BibleLicensedLoadRecovery {
    public static let openFallbackTranslationIds = ["web", "kjv"]

    public static func isLicensedTranslationId(_ id: String) -> Bool {
        BibleTranslations.find(id: BibleTranslations.normalizeId(id))?.source == .apiBible
    }

    public static func shouldOfferOpenBibleFallback(
        translationId: String,
        httpStatus: Int? = nil,
        errorDescription: String
    ) -> Bool {
        guard isLicensedTranslationId(translationId) else { return false }
        if let httpStatus {
            switch httpStatus {
            case 401, 403, 502, 503, 504:
                return true
            case 429:
                return true
            default:
                break
            }
        }
        let lower = errorDescription.lowercased()
        if lower.contains("app attest") { return true }
        if lower.contains("licensed") { return true }
        if lower.contains("verify this device") { return true }
        if lower.contains("verify this licensed") { return true }
        if lower.contains("http 401") || lower.contains("http 403") { return true }
        if lower.contains("http 502") || lower.contains("http 503") || lower.contains("http 504") { return true }
        if lower.contains("too many bible") { return true }
        if lower.contains("temporarily unavailable") { return true }
        return false
    }
}
