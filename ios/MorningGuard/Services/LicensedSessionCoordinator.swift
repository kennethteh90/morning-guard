import Foundation

/// Caches a short-lived licensed Bible proxy session (parity with Android `LicensedSessionCoordinator`).
actor LicensedSessionCoordinator {
    struct SessionGrant: Sendable {
        var token: String
        var expiresAtEpochMs: Int64
    }

    enum CoordinatorError: Error {
        case sessionUnavailable
    }

    private var cached: SessionGrant?
    private let expirySkewMs: Int64 = 60_000
    private let mintSession: @Sendable () async throws -> SessionGrant

    init(mintSession: @escaping @Sendable () async throws -> SessionGrant) {
        self.mintSession = mintSession
    }

    func sessionToken() async throws -> String {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        if let grant = cached, grant.expiresAtEpochMs > now + expirySkewMs {
            return grant.token
        }
        let grant = try await mintSession()
        cached = grant
        return grant.token
    }

    func invalidate() {
        cached = nil
    }
}
