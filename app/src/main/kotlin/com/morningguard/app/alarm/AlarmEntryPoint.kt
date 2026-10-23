package com.morningguard.app.alarm

import com.morningguard.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlarmEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun morningAlarmScheduler(): MorningAlarmScheduler
}
