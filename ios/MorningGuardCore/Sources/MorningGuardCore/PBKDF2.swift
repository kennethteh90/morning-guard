import CommonCrypto
import Foundation

public enum NoteCryptoConstants {
    public static let pbkdf2Iterations = 120_000
}

public enum PBKDF2 {
    public static func deriveKey(
        password: Data,
        salt: Data,
        iterations: Int = NoteCryptoConstants.pbkdf2Iterations,
        keyLength: Int = 32
    ) -> Data {
        var derived = Data(count: keyLength)
        let status = derived.withUnsafeMutableBytes { derivedBytes in
            salt.withUnsafeBytes { saltBytes in
                password.withUnsafeBytes { passwordBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                        password.count,
                        saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        derivedBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        keyLength
                    )
                }
            }
        }
        guard status == kCCSuccess else {
            fatalError("PBKDF2 failed")
        }
        return derived
    }
}
