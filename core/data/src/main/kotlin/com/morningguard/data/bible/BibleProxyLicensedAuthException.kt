package com.morningguard.data.bible

import java.io.IOException

/**
 * Attestation or licensed-session failure. Extends [IOException] so OkHttp/Retrofit
 * treat it as a handled network failure instead of crashing the process.
 */
open class BibleProxyLicensedAuthException(
    message: String = USER_FACING_MESSAGE,
    cause: Throwable? = null,
) : IOException(message, cause) {
    companion object {
        const val USER_FACING_MESSAGE =
            "Couldn't verify this licensed Bible translation right now. " +
                "Switch to WEB or KJV, or try again in a minute."
    }
}
