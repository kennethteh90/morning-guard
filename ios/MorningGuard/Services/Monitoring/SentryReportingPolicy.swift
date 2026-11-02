import Foundation
import Sentry

enum SentryReportingPolicy {
    static func shouldReport(_ error: Error) -> Bool {
        if isCoroutineCancellation(error) {
            return false
        }
        if isCredentialUserCancellation(error) {
            return false
        }
        return true
    }

    static func shouldDrop(_ event: Event) -> Bool {
        guard let exceptions = event.exceptions, !exceptions.isEmpty else {
            return false
        }
        return exceptions.all { isBenignException($0) }
    }

    private static func isCoroutineCancellation(_ error: Error) -> Bool {
        if error is CancellationError {
            return true
        }
        let ns = error as NSError
        if ns.domain == NSCocoaErrorDomain && ns.code == NSUserCancelledError {
            return true
        }
        let message = String(describing: error).lowercased()
        if message.contains("cancelled") || message.contains("canceled") {
            if message.contains("job was cancelled") || message.contains("user") {
                return true
            }
        }
        return false
    }

    private static func isCredentialUserCancellation(_ error: Error) -> Bool {
        let name = String(describing: type(of: error))
        return name.contains("GetCredentialCancellationException") ||
            name.contains("AuthenticationServices") && name.contains("Cancel")
    }

    private static func isBenignException(_ exception: Exception) -> Bool {
        let type = exception.type ?? ""
        if type.hasSuffix("CancellationException") ||
            type.contains("GetCredentialCancellationException") {
            return true
        }
        let value = exception.value ?? ""
        if value.localizedCaseInsensitiveContains("job was cancelled") ||
            value.localizedCaseInsensitiveContains("cancelled by user") ||
            value.localizedCaseInsensitiveContains("activity is cancelled by the user") {
            return true
        }
        return false
    }
}
