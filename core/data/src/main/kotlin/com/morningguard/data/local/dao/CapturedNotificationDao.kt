package com.morningguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.morningguard.data.local.entity.CapturedNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedNotificationDao {
    @Insert
    suspend fun insert(entity: CapturedNotificationEntity): Long

    @Update
    suspend fun update(entity: CapturedNotificationEntity)

    @Query("SELECT * FROM captured_notifications WHERE notificationKey = :key LIMIT 1")
    suspend fun findByNotificationKey(key: String): CapturedNotificationEntity?

    @Query(
        """
        INSERT INTO captured_notifications (packageName, title, text, postedAtEpochMs, category, notificationKey)
        VALUES (:packageName, :title, :text, :postedAtEpochMs, :category, :notificationKey)
        ON CONFLICT(notificationKey) DO UPDATE SET
            packageName = excluded.packageName,
            title = excluded.title,
            text = excluded.text,
            postedAtEpochMs = excluded.postedAtEpochMs,
            category = excluded.category
        """,
    )
    suspend fun upsertByNotificationKey(
        packageName: String,
        title: String,
        text: String,
        postedAtEpochMs: Long,
        category: String?,
        notificationKey: String,
    )

    @Query("SELECT * FROM captured_notifications ORDER BY postedAtEpochMs DESC")
    fun observeAll(): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT COUNT(*) FROM captured_notifications")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM captured_notifications")
    suspend fun count(): Int

    @Query("SELECT * FROM captured_notifications ORDER BY postedAtEpochMs DESC LIMIT 1")
    fun observeLatest(): Flow<CapturedNotificationEntity?>

    @Query("SELECT * FROM captured_notifications ORDER BY postedAtEpochMs DESC LIMIT 1")
    suspend fun latest(): CapturedNotificationEntity?

    @Query("DELETE FROM captured_notifications")
    suspend fun clearAll()

    @Query("DELETE FROM captured_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM captured_notifications
            WHERE packageName = :packageName AND title = :title AND text = :text
        )
        """,
    )
    suspend fun existsDuplicate(packageName: String, title: String, text: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM captured_notifications
            WHERE packageName = :packageName AND title = :title COLLATE NOCASE
        )
        """,
    )
    suspend fun existsSameTitle(packageName: String, title: String): Boolean

    @Query("SELECT * FROM captured_notifications WHERE packageName = :packageName")
    suspend fun findByPackage(packageName: String): List<CapturedNotificationEntity>

    @Query("DELETE FROM captured_notifications WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("SELECT DISTINCT packageName FROM captured_notifications ORDER BY packageName")
    suspend fun distinctPackageNames(): List<String>
}
