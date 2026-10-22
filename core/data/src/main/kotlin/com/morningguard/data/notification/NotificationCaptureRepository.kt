package com.morningguard.data.notification

import com.morningguard.data.local.dao.CapturedNotificationDao
import com.morningguard.data.local.entity.toEntity
import com.morningguard.data.local.entity.toDomain
import com.morningguard.domain.model.CapturedNotification
import com.morningguard.domain.model.DigestSummary
import com.morningguard.domain.notification.CaptureQualityPolicy
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

    suspend fun capture(notification: CapturedNotification): Long? {
        if (!CaptureQualityPolicy.shouldPersist(
                notification.packageName,
                notification.title,
                notification.text,
            )
        ) {
            return null
        }
        val stableKey = notification.notificationKey?.trim()?.takeIf { it.isNotEmpty() }
        if (stableKey != null) {
            dao.upsertByNotificationKey(
                packageName = notification.packageName,
                title = notification.title,
                text = notification.text,
                postedAtEpochMs = notification.postedAtEpochMs,
                category = notification.category,
                notificationKey = stableKey,
            )
            return dao.findByNotificationKey(stableKey)?.id
        }
        if (dao.existsDuplicate(notification.packageName, notification.title, notification.text)) {
            return null
        }
        if (CaptureQualityPolicy.shouldSkipDuplicateTitle(notification.packageName, notification.title) &&
            dao.existsSameTitle(notification.packageName, notification.title)
        ) {
            return null
        }
        val samePackage = dao.findByPackage(notification.packageName)
        if (samePackage.any { entity ->
                val existing = entity.toDomain()
                CaptureQualityPolicy.isNearDuplicate(existing, notification) ||
                    CaptureQualityPolicy.isSameBodyDuplicate(existing, notification)
            }
        ) {
            return null
        }
        return dao.insert(notification.toEntity())
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun remove(id: Long) {
        if (id > 0) dao.deleteById(id)
    }

    suspend fun removeByPackage(packageName: String) {
        dao.deleteByPackage(packageName)
    }

    suspend fun distinctNotifyingPackageNames(): List<String> = dao.distinctPackageNames()

    suspend fun snapshotDigestSummary(): DigestSummary {
        val count = dao.count()
        val latest = dao.latest()
        return DigestSummary(
            count = count,
            latestTitle = latest?.title.orEmpty(),
            latestText = latest?.text.orEmpty(),
            latestPackage = latest?.packageName.orEmpty(),
        )
    }
}
