package com.morningguard.app.notification

import android.app.Notification
import android.os.Bundle

object NotificationExtrasReader {
    fun readTitleAndText(extras: Bundle): Pair<String, String> {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (text.isBlank()) {
            text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        }
        if (text.isBlank()) {
            @Suppress("DEPRECATION")
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (lines != null && lines.isNotEmpty()) {
                text = lines.joinToString("\n") { it.toString() }
            }
        }
        return title to text
    }
}
