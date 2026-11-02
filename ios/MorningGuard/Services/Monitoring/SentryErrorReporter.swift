import Foundation
import Sentry

struct SentryErrorReporter: ErrorReporter {
    func reportNonFatal(throwable: Error, tags: [String: String], extras: [String: String]) {
        guard SentryReportingPolicy.shouldReport(throwable) else { return }
        SentrySDK.capture(error: throwable) { scope in
            scope.setLevel(.warning)
            for (key, value) in tags {
                scope.setTag(value: String(value.prefix(200)), key: key)
            }
            for (key, value) in extras {
                scope.setExtra(value: String(value.prefix(2000)), key: key)
            }
        }
    }

    func breadcrumb(category: String, message: String) {
        let crumb = Breadcrumb(level: .info, category: String(category.prefix(64)))
        crumb.message = String(message.prefix(2000))
        SentrySDK.addBreadcrumb(crumb)
    }
}

enum SentryBootstrap {
    static func startIfConfigured() {
        guard SentryConfig.isConfigured else { return }
        SentrySDK.start { options in
            options.dsn = SentryConfig.dsn
            #if DEBUG
            options.debug = false
            options.environment = "debug"
            options.tracesSampleRate = 0
            #else
            options.environment = "production"
            options.tracesSampleRate = 0.1
            #endif
            options.sendDefaultPii = false
            if let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
                options.releaseName = "com.morningguard.app@\(version)"
            }
            if let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String {
                options.dist = build
            }
            options.beforeSend = { event in
                if SentryReportingPolicy.shouldDrop(event) {
                    return nil
                }
                event.request?.headers?.removeValue(forKey: "Authorization")
                event.request?.headers?.removeValue(forKey: "apikey")
                if let message = event.message?.message {
                    event.message?.message = SentryPrivacyScrubber.redact(message)
                }
                event.exceptions = event.exceptions?.map { exception in
                    exception.value = SentryPrivacyScrubber.redact(exception.value)
                    return exception
                }
                if let extra = event.extra {
                    for (key, value) in extra {
                        if let string = value as? String {
                            event.extra?[key] = SentryPrivacyScrubber.redact(string)
                        }
                    }
                }
                event.breadcrumbs = event.breadcrumbs?.map { crumb in
                    if let message = crumb.message {
                        crumb.message = SentryPrivacyScrubber.redact(message)
                    }
                    return crumb
                }
                return event
            }
        }
    }
}
