package com.morningguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.morningguard.data.local.entity.CapturedNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedNotificationDao {
    @Insert
    suspend fun insert(entity: CapturedNotificationEntity): Long

    @Query("SELECT * FROM captured_notifications ORDER BY postedAtEpochMs DESC")
    fun observeAll(): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT COUNT(*) FROM captured_notifications")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM captured_notifications ORDER BY postedAtEpochMs DESC LIMIT 1")
    fun observeLatest(): Flow<CapturedNotificationEntity?>

    @Query("DELETE FROM captured_notifications")
    suspend fun clearAll()
}
