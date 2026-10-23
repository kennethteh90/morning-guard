package com.morningguard.app.sync

object SyncOwnership {
    fun shouldUploadLocalHighlights(boundUserId: String?, currentUserId: String): Boolean =
        boundUserId == null || boundUserId == currentUserId
}
