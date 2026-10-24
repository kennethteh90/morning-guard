import Foundation

protocol ErrorReporter: Sendable {
    func reportNonFatal(throwable: Error, tags: [String: String], extras: [String: String])
    func breadcrumb(category: String, message: String)
}

struct NoOpErrorReporter: ErrorReporter {
    func reportNonFatal(throwable: Error, tags: [String: String], extras: [String: String]) {}
    func breadcrumb(category: String, message: String) {}
}
