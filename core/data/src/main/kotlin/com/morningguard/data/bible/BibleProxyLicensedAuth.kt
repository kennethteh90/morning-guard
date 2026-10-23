package com.morningguard.data.bible

import okhttp3.Request

/**
 * Adds attestation / session headers for licensed Bible proxy chapter requests (NIV, NKJV, NLT).
 * Open-domain translations do not use this.
 */
interface BibleProxyLicensedAuth {
    fun applyLicensedHeaders(builder: Request.Builder)

    /** Drop any cached short-lived session so the next call re-attests. */
    fun invalidateSession() = Unit
}

object NoOpBibleProxyLicensedAuth : BibleProxyLicensedAuth {
    override fun applyLicensedHeaders(builder: Request.Builder) = Unit
}
