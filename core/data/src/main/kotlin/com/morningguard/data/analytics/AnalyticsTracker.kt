package com.morningguard.data.analytics

interface AnalyticsTracker {
    fun track(event: String, properties: Map<String, String> = emptyMap())
}

class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: String, properties: Map<String, String>) = Unit
}

class LoggingAnalyticsTracker : AnalyticsTracker {
    override fun track(event: String, properties: Map<String, String>) {
        android.util.Log.i("MorningGuardAnalytics", "$event $properties")
    }
}
