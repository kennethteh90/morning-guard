package com.morningguard.data.bible

import okhttp3.Request

/**
 * Adds attestation headers for licensed Bible proxy chapter requests (NIV, NKJV, NLT).
 * Open-domain translations do not use this.
 */
interface BibleProxyLicensedAuth {
    fun applyLicensedHeaders(builder: Request.Builder)
}

object NoOpBibleProxyLicensedAuth : BibleProxyLicensedAuth {
    override fun applyLicensedHeaders(builder: Request.Builder) = Unit
}
