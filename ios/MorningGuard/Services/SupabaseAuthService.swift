import Foundation
import Observation
import Supabase

struct AuthUser: Equatable, Sendable {
    var id: String
    var email: String?
}

enum AuthState: Equatable {
    case unconfigured
    case signedOut
    case signedIn(AuthUser)
    case error(String)
}

@MainActor
@Observable
final class SupabaseAuthService {
    private(set) var state: AuthState = .unconfigured
    private var client: SupabaseClient?

    init() {
        if SupabaseConfig.isConfigured, let baseURL = URL(string: SupabaseConfig.url) {
            let redirect = URL(string: SupabaseConfig.magicLinkRedirect)
            let options = SupabaseClientOptions(
                auth: .init(
                    redirectToURL: redirect,
                    flowType: .pkce
                )
            )
            client = SupabaseClient(
                supabaseURL: baseURL,
                supabaseKey: SupabaseConfig.anonKey,
                options: options
            )
            Task { await restoreSession() }
        } else {
            state = .unconfigured
        }
    }

    var isSignedIn: Bool {
        if case .signedIn = state { return true }
        return false
    }

    func sendMagicLink(email: String) async throws {
        guard let client else { throw AuthServiceError.notConfigured }
        let normalized = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard normalized.contains("@") else { throw AuthServiceError.invalidEmail }
        let wait = MagicLinkRateLimitStore.secondsUntilResend(email: normalized)
        if wait > 0 {
            throw MagicLinkRateLimitedError(retryAfterSeconds: wait)
        }
        let redirect = URL(string: SupabaseConfig.magicLinkRedirect)!
        do {
            try await client.auth.signInWithOTP(email: normalized, redirectTo: redirect)
            MagicLinkRateLimitStore.recordSent(email: normalized)
            state = .signedOut
        } catch {
            throw mapAuthError(error)
        }
    }

    func handleAuthCallback(url: URL) async throws {
        guard let client else { throw AuthServiceError.notConfigured }
        guard AuthCallbackURL.isSignInCallback(url) else {
            throw AuthServiceError.signInIncomplete
        }
        do {
            try await client.auth.session(from: url)
            await refreshUser()
            guard isSignedIn else {
                state = .error("Could not finish sign-in. Request a new magic link and open it on this phone.")
                throw AuthServiceError.signInIncomplete
            }
        } catch {
            let mapped = mapAuthError(error)
            state = .error(mapped.localizedDescription)
            throw mapped
        }
    }

    func signOut() async throws {
        guard let client else { return }
        try await client.auth.signOut()
        state = .signedOut
    }

    func deleteCurrentUser() async throws {
        guard let client else { throw AuthServiceError.notConfigured }
        let session = try await client.auth.session
        let base = SupabaseConfig.url.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = URL(string: "\(base)/auth/v1/user") else {
            throw AuthServiceError.accountDeletionFailed
        }
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(SupabaseConfig.anonKey, forHTTPHeaderField: "apikey")
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw AuthServiceError.accountDeletionFailed
        }
        try await client.auth.signOut()
        state = .signedOut
    }

    func clientOrNil() -> SupabaseClient? {
        client
    }

    func currentUserId() async throws -> String {
        guard let client else { throw AuthServiceError.notConfigured }
        let session = try await client.auth.session
        return session.user.id.uuidString.lowercased()
    }

    func restoreSession() async {
        guard let client else { return }
        do {
            _ = try await client.auth.session
            await refreshUser()
        } catch {
            state = .signedOut
        }
    }

    private func refreshUser() async {
        guard let client else { return }
        do {
            let session = try await client.auth.session
            let user = session.user
            state = .signedIn(AuthUser(id: user.id.uuidString.lowercased(), email: user.email))
        } catch {
            state = .signedOut
        }
    }

    private func mapAuthError(_ error: Error) -> Error {
        let raw = error.localizedDescription
        if raw.contains("429") || raw.localizedCaseInsensitiveContains("rate limit")
            || raw.localizedCaseInsensitiveContains("over_email_send_rate_limit") {
            return AuthServiceError.tooManyEmails
        }
        return error
    }
}

enum AuthServiceError: LocalizedError {
    case notConfigured
    case invalidEmail
    case signInIncomplete
    case accountDeletionFailed
    case tooManyEmails

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "Cloud sync is not configured in this build."
        case .invalidEmail: return "Enter a valid email address."
        case .signInIncomplete: return "Could not finish sign-in."
        case .accountDeletionFailed: return "Account deletion failed. Try again or contact support."
        case .tooManyEmails: return "Too many sign-in emails from Supabase. Wait a few minutes, then try again."
        }
    }
}
