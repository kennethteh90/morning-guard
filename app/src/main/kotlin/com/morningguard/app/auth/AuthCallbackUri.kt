package com.morningguard.app.auth

import android.net.Uri

object AuthCallbackUri {

    /** Must match AndroidManifest HTTPS app link host. */
    const val AUTH_LANDING_HOST = "morning-guard.codingagents.cc"

    fun isAuthCallback(uri: Uri?): Boolean {
        if (uri == null) return false
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) return false
        val custom = uri.scheme == "morningguard" && uri.host == "auth"
        val appLink = uri.scheme == "https" && uri.host == AUTH_LANDING_HOST
        return custom || appLink
    }
}
