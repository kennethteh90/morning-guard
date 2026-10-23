package com.morningguard.app.screenshot

import android.content.Context
import android.content.Intent
import com.morningguard.app.BuildConfig
import java.io.File

object ScreenshotReadyNotifier {
    private const val READY_FILE = "screenshot_scene_ready"

    fun markReady(context: Context, scene: String) {
        if (!BuildConfig.SCREENSHOT_MODE) return
        File(context.filesDir, READY_FILE).writeText(scene)
        context.sendBroadcast(
            Intent(ScreenshotIntents.ACTION_SCENE_READY).apply {
                setPackage(context.packageName)
                putExtra(ScreenshotIntents.EXTRA_READY_SCENE, scene)
            },
        )
    }

    fun clear(context: Context) {
        File(context.filesDir, READY_FILE).delete()
    }
}
