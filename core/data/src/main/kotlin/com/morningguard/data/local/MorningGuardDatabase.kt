package com.morningguard.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.morningguard.data.local.dao.CapturedNotificationDao
import com.morningguard.data.local.entity.CapturedNotificationEntity

@Database(
    entities = [CapturedNotificationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MorningGuardDatabase : RoomDatabase() {
    abstract fun capturedNotificationDao(): CapturedNotificationDao
}
