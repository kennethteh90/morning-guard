import Foundation

public enum RecoveryPasswordRules {
    public static let minLength = 8

    public static func validate(password: String) -> String? {
        if password.count < minLength {
            return "Use at least \(minLength) characters."
        }
        let hasLetter = password.unicodeScalars.contains { CharacterSet.letters.contains($0) }
        let hasDigit = password.unicodeScalars.contains { CharacterSet.decimalDigits.contains($0) }
        if !hasLetter || !hasDigit {
            return "Include at least one letter and one number."
        }
        return nil
    }
}
