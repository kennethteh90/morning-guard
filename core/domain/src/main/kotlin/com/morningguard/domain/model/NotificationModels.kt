package com.morningguard.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CapturedNotification(
    val id: Long = 0,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtEpochMs: Long,
    val category: String? = null,
    /** Stable system notification key from the listener when captured. */
    val notificationKey: String? = null,
)

@Serializable
data class DigestSummary(
    val count: Int,
    val latestTitle: String,
    val latestText: String,
    val latestPackage: String,
)
