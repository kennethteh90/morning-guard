package com.morningguard.domain.notification

import com.morningguard.domain.model.CapturedNotification

object CaptureQualityPolicy {
    /** Packages whose empty placeholders should never appear in the digest. */
    val skipWhenEmptyPackages: Set<String> = setOf(
        "com.google.android.googlequicksearchbox",
    )

    /**
     * Outlook (and similar) often posts twice per message with the same subject but different preview text.
     * Treat same package + title as one digest row.
     */
    val dedupeByTitleOnlyPackages: Set<String> = setOf(
        "com.microsoft.office.outlook",
        "com.google.android.gm",
    )

    private val genericInboxTitles: Set<String> = setOf(
        "new message",
        "new messages",
        "1 new message",
        "2 new messages",
        "3 new messages",
        "new mail",
        "new emails",
    )

    fun shouldPersist(
        packageName: String,
        title: String,
        text: String,
    ): Boolean {
        val trimmedTitle = title.trim()
        val trimmedText = text.trim()

        if (trimmedTitle.isEmpty() && trimmedText.isEmpty()) {
            return false
        }
        if (isGenericInboxSummary(trimmedTitle, trimmedText)) {
            return false
        }
        return true
    }

    fun isDuplicate(existing: CapturedNotification, candidate: CapturedNotification): Boolean {
        if (existing.packageName != candidate.packageName) return false
        if (!existing.title.equals(candidate.title, ignoreCase = true)) return false
        if (existing.text.equals(candidate.text, ignoreCase = true)) return true
        return existing.packageName in dedupeByTitleOnlyPackages
    }

    fun isNearDuplicate(existing: CapturedNotification, candidate: CapturedNotification): Boolean {
        if (isDuplicate(existing, candidate)) return true
        if (existing.packageName != candidate.packageName) return false
        if (!existing.title.equals(candidate.title, ignoreCase = true)) return false
        val existingText = normalizeText(existing.text)
        val candidateText = normalizeText(candidate.text)
        if (existingText.isEmpty() || candidateText.isEmpty()) return false
        if (existingText == candidateText) return true
        val minOverlap = 12
        if (existingText.length >= minOverlap && candidateText.length >= minOverlap) {
            if (existingText.contains(candidateText) || candidateText.contains(existingText)) {
                return true
            }
        }
        return false
    }

    fun isSameBodyDuplicate(existing: CapturedNotification, candidate: CapturedNotification): Boolean {
        if (existing.packageName != candidate.packageName) return false
        val existingText = normalizeText(existing.text)
        val candidateText = normalizeText(candidate.text)
        if (existingText.isEmpty() || candidateText.isEmpty()) return false
        return existingText == candidateText
    }

    private fun normalizeText(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    fun shouldSkipDuplicateTitle(packageName: String, title: String): Boolean {
        if (packageName !in dedupeByTitleOnlyPackages) return false
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == packageName) return false
        return true
    }

    private fun isGenericInboxSummary(title: String, text: String): Boolean {
        if (title.lowercase() !in genericInboxTitles) return false
        return text.isEmpty() || text.equals(title, ignoreCase = true)
    }
}
