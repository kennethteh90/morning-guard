import Foundation

public enum BibleTranslationSource: Sendable {
    case openBible
    case apiBible
}

public struct BibleTranslationOption: Sendable, Equatable {
    public var id: String
    public var chipLabel: String
    public var fullName: String
    public var language: String
    public var source: BibleTranslationSource

    public init(
        id: String,
        chipLabel: String,
        fullName: String,
        language: String = "English",
        source: BibleTranslationSource = .openBible
    ) {
        self.id = id
        self.chipLabel = chipLabel
        self.fullName = fullName
        self.language = language
        self.source = source
    }
}

public enum BibleTranslations {
    public static let supported: [BibleTranslationOption] = [
        BibleTranslationOption(id: "niv", chipLabel: "NIV", fullName: "New International Version", source: .apiBible),
        BibleTranslationOption(id: "nkjv", chipLabel: "NKJV", fullName: "New King James Version", source: .apiBible),
        BibleTranslationOption(id: "nlt", chipLabel: "NLT", fullName: "New Living Translation", source: .apiBible),
        BibleTranslationOption(id: "web", chipLabel: "WEB", fullName: "World English Bible"),
        BibleTranslationOption(id: "kjv", chipLabel: "KJV", fullName: "King James Version"),
        BibleTranslationOption(id: "asv", chipLabel: "ASV", fullName: "American Standard Version (1901)"),
        BibleTranslationOption(id: "bbe", chipLabel: "BBE", fullName: "Bible in Basic English"),
        BibleTranslationOption(id: "darby", chipLabel: "DARBY", fullName: "Darby Bible"),
        BibleTranslationOption(id: "dra", chipLabel: "DRA", fullName: "Douay-Rheims 1899"),
        BibleTranslationOption(id: "ylt", chipLabel: "YLT", fullName: "Young's Literal Translation"),
        BibleTranslationOption(id: "oeb-us", chipLabel: "OEB-US", fullName: "Open English Bible (US)"),
        BibleTranslationOption(id: "oeb-cw", chipLabel: "OEB-CW", fullName: "Open English Bible (Commonwealth)"),
        BibleTranslationOption(id: "webbe", chipLabel: "WEB-BE", fullName: "World English Bible (British)"),
        BibleTranslationOption(id: "clementine", chipLabel: "VULG", fullName: "Clementine Latin Vulgate", language: "Latin"),
        BibleTranslationOption(id: "cuv", chipLabel: "CUV", fullName: "Chinese Union Version", language: "Chinese"),
        BibleTranslationOption(id: "bkr", chipLabel: "BKR", fullName: "Bible kralická", language: "Czech"),
        BibleTranslationOption(id: "almeida", chipLabel: "ALMEIDA", fullName: "João Ferreira de Almeida", language: "Portuguese"),
        BibleTranslationOption(id: "rccv", chipLabel: "RCCV", fullName: "Protestant Romanian Cornilescu", language: "Romanian"),
        BibleTranslationOption(id: "cherokee", chipLabel: "CHEROKEE", fullName: "Cherokee New Testament", language: "Cherokee"),
    ]

    public static let defaultId = "niv"

    public static func find(id: String) -> BibleTranslationOption? {
        supported.first { $0.id.caseInsensitiveCompare(id) == .orderedSame }
    }

    public static func normalizeId(_ id: String) -> String {
        find(id: id)?.id ?? defaultId
    }

    public static func chipLabel(id: String) -> String {
        find(id: id)?.chipLabel ?? id.uppercased()
    }

    public static func openBibleOptions() -> [BibleTranslationOption] {
        supported.filter { $0.source == .openBible }
    }

    public static func apiBibleOptions() -> [BibleTranslationOption] {
        supported.filter { $0.source == .apiBible }
    }
}
