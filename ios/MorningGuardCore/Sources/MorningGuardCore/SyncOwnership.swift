import Foundation

public enum SyncOwnership {
    public static func shouldUploadLocalHighlights(boundUserId: String?, currentUserId: String) -> Bool {
        guard let bound = boundUserId, !bound.isEmpty else { return true }
        return bound == currentUserId
    }
}
