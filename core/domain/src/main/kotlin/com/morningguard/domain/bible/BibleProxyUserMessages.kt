package com.morningguard.domain.bible

object BibleProxyUserMessages {
    fun friendlyMessage(httpStatus: Int, serverError: String?): String {
        if (httpStatus == 429) {
            return "Too many Bible chapter requests from this network. Wait about a minute, then try again."
        }
        val base = serverError?.takeIf { it.isNotBlank() }
            ?: "Bible server error (HTTP $httpStatus)"
        if (httpStatus == 403 && base.contains("Licensed", ignoreCase = true)) {
            return "$base Switch to WEB or KJV in the reader, or run " +
                "`wrangler secret put API_BIBLE_LICENSED_ENABLED` (value: true) on the bible-cache worker."
        }
        return base
    }
}
