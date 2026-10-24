import Foundation

/// User-facing copy backed by `Localizable.xcstrings` (English).
enum L10n {
    static func tr(_ key: String) -> String {
        String(localized: String.LocalizationValue(key))
    }

    static func tr(_ key: String, _ args: CVarArg...) -> String {
        let format = tr(key)
        return String(format: format, locale: Locale.current, arguments: args)
    }
}
