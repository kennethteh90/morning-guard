package com.morningguard.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.morningguard.app.MainActivity
import com.morningguard.app.R
import com.morningguard.app.platform.AppDisplayNameResolver
import com.morningguard.domain.model.DigestSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigestNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDisplayNameResolver: AppDisplayNameResolver,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.digest_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.digest_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showDigest(summary: DigestSummary) {
        if (summary.count == 0) {
            notificationManager.cancel(DIGEST_NOTIFICATION_ID)
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_DIGEST, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val latestApp = appDisplayNameResolver.labelFor(summary.latestPackage)
        val content = context.resources.getQuantityString(
            R.plurals.digest_held_count,
            summary.count,
            summary.count,
            latestApp,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(content)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(DIGEST_NOTIFICATION_ID, notification)
    }

    fun clearDigest() {
        notificationManager.cancel(DIGEST_NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "morning_guard_digest"
        const val DIGEST_NOTIFICATION_ID = 1001
        const val EXTRA_OPEN_DIGEST = "com.morningguard.app.extra.OPEN_DIGEST"
    }
}
