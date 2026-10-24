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
        val normalized = if (stableKey != null) {
            notification.copy(notificationKey = stableKey)
        } else {
            notification.copy(notificationKey = null)
        }
        if (stableKey != null) {
            dao.upsertByNotificationKey(
                packageName = normalized.packageName,
                title = normalized.title,
                text = normalized.text,
                postedAtEpochMs = normalized.postedAtEpochMs,
                category = normalized.category,
                notificationKey = stableKey,
            )
            return dao.findByNotificationKey(stableKey)?.id
        }
        if (dao.existsDuplicate(normalized.packageName, normalized.title, normalized.text)) {
            return null
        }
        if (CaptureQualityPolicy.shouldSkipDuplicateTitle(normalized.packageName, normalized.title) &&
            dao.existsSameTitle(normalized.packageName, normalized.title)
        ) {
            return null
        }
        val samePackage = dao.findByPackage(normalized.packageName)
        if (samePackage.any { entity ->
                val existing = entity.toDomain()
                CaptureQualityPolicy.isNearDuplicate(existing, normalized) ||
                    CaptureQualityPolicy.isSameBodyDuplicate(existing, normalized)
            }
        ) {
            return null
        }
        val insertedId = dao.insert(normalized.toEntity())
        return insertedId.takeIf { it != -1L }
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
