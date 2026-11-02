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
    private var cooldownUntilMs: Int64 = 0
    private let expirySkewMs: Int64 = 60_000
    private let cooldownMs: Int64 = 60_000
    private let maxAttempts = 4
    private let mintSession: @Sendable () async throws -> SessionGrant

    init(mintSession: @escaping @Sendable () async throws -> SessionGrant) {
        self.mintSession = mintSession
    }

    func sessionToken() async throws -> String {
        let now = Self.nowMs()
        if let grant = cached, grant.expiresAtEpochMs > now + expirySkewMs {
            return grant.token
        }
        if now < cooldownUntilMs {
            throw CoordinatorError.sessionUnavailable
        }
        let grant = try await mintWithRetry()
        cached = grant
        return grant.token
    }

    func invalidate() {
        cached = nil
    }

    private func mintWithRetry() async throws -> SessionGrant {
        var lastError: Error?
        for attempt in 0..<maxAttempts {
            do {
                return try await mintSession()
            } catch {
                lastError = error
                if !Self.isTransient(error) || attempt == maxAttempts - 1 {
                    cooldownUntilMs = Self.nowMs() + cooldownMs
                    throw error
                }
                try await Task.sleep(nanoseconds: Self.backoffMs(attemptZeroBased: attempt) * 1_000_000)
            }
        }
        throw lastError ?? CoordinatorError.sessionUnavailable
    }

    static func isTransient(_ error: Error) -> Bool {
        if error is SessionEndpointUnavailable { return false }
        if let urlError = error as? URLError {
            switch urlError.code {
            case .timedOut, .networkConnectionLost, .notConnectedToInternet, .cannotConnectToHost:
                return true
            default:
                break
            }
        }
        if case let BibleProxyError.http(status, _) = error {
            return status == 401 || status == 502 || status == 503 || status == 504
        }
        return false
    }

    static func backoffMs(attemptZeroBased: Int) -> UInt64 {
        switch attemptZeroBased {
        case 0: return 1_000
        case 1: return 3_000
        default: return 6_000
        }
    }

    private static func nowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}
