package com.morningguard.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.morningguard.data.local.dao.BibleHighlightDao
import com.morningguard.data.local.dao.CapturedNotificationDao
import com.morningguard.data.local.entity.BibleHighlightEntity
import com.morningguard.data.local.entity.CapturedNotificationEntity

@Database(
    entities = [
        CapturedNotificationEntity::class,
        BibleHighlightEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class MorningGuardDatabase : RoomDatabase() {
    abstract fun capturedNotificationDao(): CapturedNotificationDao

    abstract fun bibleHighlightDao(): BibleHighlightDao
}
