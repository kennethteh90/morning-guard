package com.morningguard.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DataStorageMode {
    /** Data stays on device; cloud sync is disabled even if a session exists. */
    LOCAL_ONLY,

    /** User opted into magic-link / account sync. */
    CLOUD_SYNC,
}
