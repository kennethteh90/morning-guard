package com.morningguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.morningguard.data.local.entity.BibleHighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleHighlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BibleHighlightEntity): Long

    @Update
    suspend fun update(entity: BibleHighlightEntity)

    @Query("SELECT * FROM bible_highlights ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<BibleHighlightEntity>>

    @Query("SELECT * FROM bible_highlights ORDER BY createdAtEpochMs DESC")
    suspend fun listAll(): List<BibleHighlightEntity>

    @Query("SELECT * FROM bible_highlights WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: Long): BibleHighlightEntity?

    @Query("DELETE FROM bible_highlights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bible_highlights WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: Long)

    @Query("DELETE FROM bible_highlights")
    suspend fun clearAll()
}
