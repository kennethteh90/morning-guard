import Foundation

enum SentryConfig {
    static var dsn: String {
        (Bundle.main.object(forInfoDictionaryKey: "SENTRY_DSN") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static var isConfigured: Bool {
        !dsn.isEmpty
    }
}
