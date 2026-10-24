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
            client = SupabaseClient(supabaseURL: baseURL, supabaseKey: SupabaseConfig.anonKey)
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
        let redirect = URL(string: SupabaseConfig.magicLinkRedirect)!
        try await client.auth.signInWithOTP(email: normalized, redirectTo: redirect)
        state = .signedOut
    }

    func handleAuthCallback(url: URL) async throws {
        guard let client else { throw AuthServiceError.notConfigured }
        try await client.auth.session(from: url)
        await refreshUser()
        guard isSignedIn else {
            state = .error("Could not finish sign-in. Request a new magic link and open it on this phone.")
            throw AuthServiceError.signInIncomplete
        }
    }

    func signOut() async throws {
        guard let client else { return }
        try await client.auth.signOut()
        state = .signedOut
    }

    func accessToken() async throws -> String {
        guard let client else { throw AuthServiceError.notConfigured }
        let session = try await client.auth.session
        return session.accessToken
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
}

enum AuthServiceError: LocalizedError {
    case notConfigured
    case invalidEmail
    case signInIncomplete

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "Cloud sync is not configured in this build."
        case .invalidEmail: return "Enter a valid email address."
        case .signInIncomplete: return "Could not finish sign-in."
        }
    }
}
