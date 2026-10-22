package com.morningguard.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

class MorningAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val pending = goAsync()
        MorningAlarmSoundPlayer.start(appContext)
        MorningAlarmNotifier.showRinging(appContext)
        runCatching {
            val launchIntent = Intent(appContext, MorningAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            appContext.startActivity(launchIntent)
        }
        try {
            val entryPoint = EntryPointAccessors.fromApplication(appContext, AlarmEntryPoint::class.java)
            runBlocking {
                val settings = entryPoint.settingsRepository().currentSettings()
                entryPoint.morningAlarmScheduler().schedule(settings.alarm)
            }
        } finally {
            pending.finish()
        }
    }
}
