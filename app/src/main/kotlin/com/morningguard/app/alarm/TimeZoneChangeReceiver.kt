package com.morningguard.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.morningguard.app.gate.GateStateHolder
import com.morningguard.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimeZoneChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var alarmScheduler: MorningAlarmScheduler
    @Inject lateinit var gateStateHolder: GateStateHolder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> Unit
            else -> return
        }
        val pending = goAsync()
        scope.launch {
            try {
                gateStateHolder.refreshNow()
                alarmScheduler.schedule(settingsRepository.currentSettings().alarm)
            } finally {
                pending.finish()
            }
        }
    }
}