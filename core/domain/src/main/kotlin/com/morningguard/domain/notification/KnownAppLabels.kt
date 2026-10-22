package com.morningguard.domain.notification

object KnownAppLabels {
    private val labels: Map<String, String> = mapOf(
        "com.microsoft.office.outlook" to "Outlook",
        "com.google.android.gm" to "Gmail",
        "com.samsung.android.email.provider" to "Samsung Email",
        "com.facebook.orca" to "Messenger",
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "com.instagram.android" to "Instagram",
        "com.twitter.android" to "X",
        "com.slack" to "Slack",
        "com.sonos.acr2" to "Sonos",
        "com.sonos.acr" to "Sonos",
        "com.spotify.music" to "Spotify",
    )

    fun labelFor(packageName: String): String? = labels[packageName]
}
