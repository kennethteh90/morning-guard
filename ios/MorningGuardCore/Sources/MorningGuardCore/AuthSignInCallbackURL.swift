import Foundation

/// PKCE magic-link callback on `morningguard://auth?code=…` (parity with Android `AuthCallbackUri` custom scheme).
public enum AuthSignInCallbackURL {
    public static func isSignInCallback(_ url: URL) -> Bool {
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
