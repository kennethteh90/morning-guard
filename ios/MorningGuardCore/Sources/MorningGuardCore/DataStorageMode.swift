import Foundation

public enum DataStorageMode: String, Codable, Sendable, Equatable {
    case localOnly = "LOCAL_ONLY"
    case cloudSync = "CLOUD_SYNC"
}
