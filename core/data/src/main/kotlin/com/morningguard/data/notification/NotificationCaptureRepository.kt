package com.morningguard.data.notification

import com.morningguard.data.local.dao.CapturedNotificationDao
import com.morningguard.data.local.entity.toEntity
import com.morningguard.data.local.entity.toDomain
import com.morningguard.domain.model.CapturedNotification
import com.morningguard.domain.model.DigestSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationCaptureRepository @Inject constructor(
    private val dao: CapturedNotificationDao,
) {
    val digestSummary: Flow<DigestSummary> = combine(
        dao.observeCount(),
        dao.observeLatest(),
    ) { count, latest ->
        DigestSummary(
            count = count,
            latestTitle = latest?.title.orEmpty(),
            latestText = latest?.text.orEmpty(),
            latestPackage = latest?.packageName.orEmpty(),
        )
    }

    val notifications: Flow<List<CapturedNotification>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun capture(notification: CapturedNotification): Long {
        return dao.insert(notification.toEntity())
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
