import Foundation

/// Fixed vectors shared with Android `NoteCryptoInteropVectorsTest` (JVM reference).
public enum NoteCryptoInteropVectors {
    public static let recoveryPassword = "TestPass1"
    public static let passwordWrapSaltBase64 = "AAECAwQFBgcICQoLDA0ODw=="
    public static let passwordWrapIvBase64 = "AAECAwQFBgcICQoL"
    public static let passwordWrappedDekBase64 =
        "LrvW3v30+5XpBqxJUdMPtU+FPK3/B6Gq1k7sbPd5Ae++QLKzEsHrVxCSXCY3XUK5"

    /// 32-byte DEK: bytes 0…31.
    public static let dekBytes: [UInt8] = Array(0..<32)

    public static let notePlaintext = "Morning reflection"
    public static let noteCiphertext =
        "mgenc1:CgsMDQ4PEBESExQVItJIpgKzsFXEYb5hu8+RIZJwzKLDU5+dhZ2gmQHvscoH/w=="
}
