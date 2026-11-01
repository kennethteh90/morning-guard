import Foundation

enum SentryPrivacyScrubber {
    private static let emailLike = try! NSRegularExpression(
        pattern: "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        options: [.caseInsensitive]
    )
    private static let bearerToken = try! NSRegularExpression(
        pattern: "Bearer\\s+\\S+",
        options: [.caseInsensitive]
    )
    private static let jwt = try! NSRegularExpression(
        pattern: "eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+",
        options: []
    )
    private static let authorizationHeader = try! NSRegularExpression(
        pattern: "Authorization:.*",
        options: [.caseInsensitive]
    )
    private static let apikeyHeader = try! NSRegularExpression(
        pattern: "apikey:.*",
        options: [.caseInsensitive]
    )

    static func redact(_ text: String) -> String {
        var result = text
        result = replacingMatches(in: result, regex: authorizationHeader, with: "Authorization: [redacted]")
        result = replacingMatches(in: result, regex: apikeyHeader, with: "apikey: [redacted]")
        result = replacingMatches(in: result, regex: bearerToken, with: "Bearer [redacted]")
        result = replacingMatches(in: result, regex: jwt, with: "[redacted-jwt]")
        result = replacingMatches(in: result, regex: emailLike, with: "[redacted-email]")
        return result
    }

    private static func replacingMatches(in text: String, regex: NSRegularExpression, with template: String) -> String {
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.stringByReplacingMatches(
            in: text,
            options: [],
            range: range,
            withTemplate: template
        )
    }
}
