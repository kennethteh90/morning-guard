import Foundation

enum SentryPrivacyScrubber {
    private static let emailLike = try! NSRegularExpression(
        pattern: "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        options: [.caseInsensitive]
    )

    static func redact(_ text: String) -> String {
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return emailLike.stringByReplacingMatches(
            in: text,
            options: [],
            range: range,
            withTemplate: "[redacted-email]"
        )
    }
}
