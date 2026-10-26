import Foundation

public enum BibleChapterCachePolicy {
    /// Matches Workers KV licensed TTL (`workers/bible-cache/src/cache-policy.ts`).
    public static let licensedTtl: TimeInterval = 30 * 24 * 60 * 60
    public static let openTtl: TimeInterval = 365 * 24 * 60 * 60

    public static func ttl(forTranslationId translationId: String) -> TimeInterval {
        if BibleTranslations.find(id: translationId)?.source == .apiBible {
            return licensedTtl
        }
        return openTtl
    }
}
