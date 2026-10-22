import SwiftUI
import MorningGuardCore

enum BibleHighlightColorsUi {
    static let savedVerseHighlightOpacity = 0.16

    static func color(for key: String) -> Color {
        switch key {
        case "green": Color(red: 0.78, green: 0.90, blue: 0.79)
        case "blue": Color(red: 0.73, green: 0.87, blue: 0.98)
        case "pink": Color(red: 0.97, green: 0.73, blue: 0.82)
        case "purple": Color(red: 0.88, green: 0.75, blue: 0.91)
        default: Color(red: 1.0, green: 0.96, blue: 0.61)
        }
    }

    static func normalizedKey(_ key: String) -> String {
        BibleHighlightColors.keys.contains(key) ? key : BibleHighlightColors.defaultKey
    }
}
