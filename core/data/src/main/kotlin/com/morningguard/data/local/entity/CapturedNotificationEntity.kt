package com.morningguard.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.morningguard.domain.model.CapturedNotification

@Entity(
    tableName = "captured_notifications",
    indices = [Index(value = ["notificationKey"], unique = true)],
)
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtEpochMs: Long,
    val category: String? = null,
    val notificationKey: String? = null,
)

fun CapturedNotificationEntity.toDomain() = CapturedNotification(
    id = id,
    packageName = packageName,
    title = title,
    text = text,
    postedAtEpochMs = postedAtEpochMs,
    category = category,
    notificationKey = notificationKey,
)

fun CapturedNotification.toEntity() = CapturedNotificationEntity(
    id = id,
    packageName = packageName,
    title = title,
    text = text,
    postedAtEpochMs = postedAtEpochMs,
    category = category,
    notificationKey = notificationKey,
)
