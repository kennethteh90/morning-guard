package com.morningguard.data.bible

data class BibleProxyConfig(
    val baseUrl: String,
    val playCloudProjectNumber: Long = 0L,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    val missingPieceMessage: String?
        get() = when {
            baseUrl.isBlank() -> "Set BIBLE_PROXY_BASE_URL in local.properties."
            else -> null
        }
}
