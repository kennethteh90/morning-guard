package com.morningguard.app.bible

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityServiceException
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.model.IntegrityErrorCode
import com.morningguard.app.BuildConfig
import com.morningguard.data.bible.BibleProxyConfig
import com.morningguard.data.bible.BibleProxyLicensedAuth
import com.morningguard.data.bible.BibleProxyLicensedAuthException
import com.morningguard.data.monitoring.ErrorReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class PlayIntegrityBibleProxyLicensedAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: BibleProxyConfig,
    private val errorReporter: ErrorReporter,
) : BibleProxyLicensedAuth {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val coordinator = LicensedSessionCoordinator(
        mintSession = { mintSessionGrant() },
        isTransientFailure = ::isTransientIntegrityFailure,
    )

    private val failureGate = LicensedAuthFailureGate(errorReporter)

    override fun applyLicensedHeaders(builder: Request.Builder) {
        val cloudProject = BuildConfig.PLAY_CLOUD_PROJECT_NUMBER.trim().toLongOrNull() ?: 0L
        if (!config.isConfigured || cloudProject <= 0L) {
            return
        }
        if (failureGate.inCooldown()) {
            throw BibleProxyLicensedAuthException()
        }
        try {
            val headers = runBlocking { licensedHeaders() }
            headers.forEach { (key, value) -> builder.header(key, value) }
        } catch (e: BibleProxyLicensedAuthException) {
            failureGate.markFailure()
            failureGate.reportFailure(e)
            throw e
        } catch (e: Exception) {
            val wrapped = BibleProxyLicensedAuthException(cause = e)
            failureGate.markFailure()
            failureGate.reportFailure(wrapped)
            throw wrapped
        }
    }

    override fun invalidateSession() {
        coordinator.invalidate()
    }

    private suspend fun licensedHeaders(): Map<String, String> {
        return try {
            mapOf(SESSION_HEADER to coordinator.sessionToken())
        } catch (_: SessionEndpointUnavailable) {
            freshAttestationHeaders()
        }
    }

    private suspend fun freshAttestationHeaders(): Map<String, String> {
        // Worker not yet configured with BIBLE_PROXY_SESSION_HMAC_SECRET — use one-shot attestation.
        val nonce = fetchChallengeNonce()
        val integrityToken = requestIntegrityTokenWithRetry(nonce)
        return mapOf(
            NONCE_HEADER to nonce,
            INTEGRITY_HEADER to integrityToken,
        )
    }

    private suspend fun mintSessionGrant(): LicensedSessionCoordinator.SessionGrant {
        val nonce = fetchChallengeNonce()
        val integrityToken = requestIntegrityToken(nonce)
        return exchangeSession(nonce, integrityToken)
    }

    private suspend fun requestIntegrityTokenWithRetry(nonce: String): String {
        // Reuse single-flight retry policy without caching a one-shot integrity token.
        val oneShot = LicensedSessionCoordinator(
            mintSession = {
                val token = requestIntegrityToken(nonce)
                LicensedSessionCoordinator.SessionGrant(
                    token = token,
                    expiresAtEpochMs = System.currentTimeMillis() + 60_000L,
                )
            },
            isTransientFailure = ::isTransientIntegrityFailure,
        )
        return oneShot.sessionToken()
    }

    private suspend fun fetchChallengeNonce(): String {
        val url = config.baseUrl.trimEnd('/') + "/v1/challenge"
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw BibleProxyLicensedAuthException(
                    "Bible proxy challenge failed (${response.code})",
                )
            }
            val body = response.body?.string() ?: throw BibleProxyLicensedAuthException(
                "Empty challenge response",
            )
            return JSONObject(body).getString("nonce")
        }
    }

    private suspend fun exchangeSession(
        nonce: String,
        integrityToken: String,
    ): LicensedSessionCoordinator.SessionGrant {
        val url = config.baseUrl.trimEnd('/') + "/v1/session"
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody("application/json".toMediaType()))
            .header(NONCE_HEADER, nonce)
            .header(INTEGRITY_HEADER, integrityToken)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (response.code == 503 || response.code == 404) {
                throw SessionEndpointUnavailable(response.code)
            }
            if (!response.isSuccessful) {
                throw BibleProxyLicensedAuthException(
                    "Licensed Bible session failed (${response.code})",
                )
            }
            val json = JSONObject(bodyText)
            val token = json.getString("sessionToken")
            val expiresAtSec = json.optLong("expiresAt", 0L)
            val expiresIn = json.optLong("expiresIn", 3600L)
            val expiresAtMs = when {
                expiresAtSec > 1_000_000_000L -> expiresAtSec * 1000L
                else -> System.currentTimeMillis() + expiresIn.coerceAtLeast(60L) * 1000L
            }
            return LicensedSessionCoordinator.SessionGrant(
                token = token,
                expiresAtEpochMs = expiresAtMs,
            )
        }
    }

    private suspend fun requestIntegrityToken(nonce: String): String = suspendCoroutine { cont ->
        val cloudProject = BuildConfig.PLAY_CLOUD_PROJECT_NUMBER.trim().toLongOrNull()
            ?: run {
                cont.resumeWithException(
                    BibleProxyLicensedAuthException("PLAY_CLOUD_PROJECT_NUMBER is not configured"),
                )
                return@suspendCoroutine
            }
        val nonceBytes = Base64.encodeToString(
            nonce.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val manager = IntegrityManagerFactory.create(context)
        val request = IntegrityTokenRequest.builder()
            .setNonce(nonceBytes)
            .setCloudProjectNumber(cloudProject)
            .build()
        manager.requestIntegrityToken(request)
            .addOnSuccessListener { response -> cont.resume(response.token()) }
            .addOnFailureListener { error -> cont.resumeWithException(error) }
    }

    /**
     * Worker returned 503/404 for `/v1/session` (secret not set yet). Extends
     * [BibleProxyLicensedAuthException] so [LicensedSessionCoordinator] rethrows it unwrapped.
     */
    private class SessionEndpointUnavailable(code: Int) :
        BibleProxyLicensedAuthException("session endpoint $code")

    companion object {
        const val NONCE_HEADER = "X-Morning-Guard-Nonce"
        const val INTEGRITY_HEADER = "X-Morning-Guard-Play-Integrity"
        const val SESSION_HEADER = "X-Morning-Guard-Session"

        fun isTransientIntegrityFailure(error: Throwable): Boolean {
            val code = (error as? IntegrityServiceException)?.errorCode ?: return false
            return code == IntegrityErrorCode.TOO_MANY_REQUESTS ||
                code == IntegrityErrorCode.CANNOT_BIND_TO_SERVICE ||
                code == IntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE
        }
    }
}
