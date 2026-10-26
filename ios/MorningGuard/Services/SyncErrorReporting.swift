import Foundation

enum SyncErrorReporting {
    static func sentryExtras(for error: Error) -> [String: String] {
        var extras = ["error_type": String(describing: type(of: error))]
        if let urlError = error as? URLError {
            extras["url_error_code"] = String(urlError.errorCode)
        }
        return extras
    }
}
