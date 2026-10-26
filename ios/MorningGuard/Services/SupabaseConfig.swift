import Foundation

enum SupabaseConfig {
    static var url: String {
        (Bundle.main.object(forInfoDictionaryKey: "SUPABASE_URL") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    static var anonKey: String {
        (Bundle.main.object(forInfoDictionaryKey: "SUPABASE_ANON_KEY") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static var isConfigured: Bool {
        guard !url.isEmpty, !anonKey.isEmpty else { return false }
        guard let parsed = URL(string: url),
              parsed.scheme == "https",
              let host = parsed.host,
              !host.isEmpty else {
            return false
        }
        return true
    }

    static var missingPieceMessage: String? {
        if url.isEmpty || anonKey.isEmpty {
            return "Set SUPABASE_URL and SUPABASE_ANON_KEY in ios/Config/Local.xcconfig."
        }
        return nil
    }

    static let magicLinkRedirect = "morningguard://auth"
}
