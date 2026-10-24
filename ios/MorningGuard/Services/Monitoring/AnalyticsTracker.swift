import Foundation
import os

protocol AnalyticsTracker: Sendable {
    func track(_ event: String, properties: [String: String])
}

struct NoOpAnalyticsTracker: AnalyticsTracker {
    func track(_ event: String, properties: [String: String]) {}
}

struct LoggingAnalyticsTracker: AnalyticsTracker {
    private static let logger = Logger(subsystem: "com.morningguard.app", category: "analytics")

    func track(_ event: String, properties: [String: String]) {
        Self.logger.info("\(event, privacy: .public) \(properties.description, privacy: .public)")
    }
}

enum AppMonitoring {
    static let errorReporter: ErrorReporter = SentryConfig.isConfigured
        ? SentryErrorReporter()
        : NoOpErrorReporter()

    static let analytics: AnalyticsTracker = {
        #if DEBUG
        return LoggingAnalyticsTracker()
        #else
        return NoOpAnalyticsTracker()
        #endif
    }()
}
