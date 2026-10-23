package com.morningguard.data.bible

data class ApiBibleConfig(
    val apiKey: String,
    /** Optional dashboard bible_id overrides keyed by translation id (niv, nkjv, nlt). */
    val bibleIdOverrides: Map<String, String> = emptyMap(),
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}
