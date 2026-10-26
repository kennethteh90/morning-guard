import Foundation

/// Matches Android `AuthCallbackUri` — PKCE `code` on host `auth` only.
enum AuthCallbackURL {
    static func isSignInCallback(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "morningguard" else { return false }
        guard url.host?.lowercased() == "auth" else { return false }
        guard let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == "code" })?
            .value
        else { return false }
        return !code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
