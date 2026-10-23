package com.morningguard.app.bible

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.morningguard.app.BuildConfig
import com.morningguard.data.bible.BibleProxyConfig
import com.morningguard.data.bible.BibleProxyLicensedAuth
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
) : BibleProxyLicensedAuth {

    private val challengeClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun applyLicensedHeaders(builder: okhttp3.Request.Builder) {
        val cloudProject = BuildConfig.PLAY_CLOUD_PROJECT_NUMBER.trim().toLongOrNull() ?: 0L
        if (!config.isConfigured || cloudProject <= 0L) {
            return
        }
        val headers = runBlocking { licensedHeaders() }
        headers.forEach { (key, value) -> builder.header(key, value) }
    }

    private suspend fun licensedHeaders(): Map<String, String> {
        val nonce = fetchChallengeNonce()
        val integrityToken = requestIntegrityToken(nonce)
        return mapOf(
            NONCE_HEADER to nonce,
            INTEGRITY_HEADER to integrityToken,
        )
    }

    private suspend fun fetchChallengeNonce(): String {
        val url = config.baseUrl.trimEnd('/') + "/v1/challenge"
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        challengeClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Bible proxy challenge failed (${response.code})" }
            val body = response.body?.string() ?: error("Empty challenge response")
            val json = JSONObject(body)
            return json.getString("nonce")
        }
    }

    private suspend fun requestIntegrityToken(nonce: String): String = suspendCoroutine { cont ->
        val cloudProject = BuildConfig.PLAY_CLOUD_PROJECT_NUMBER.trim().toLongOrNull()
            ?: error("PLAY_CLOUD_PROJECT_NUMBER is not configured")
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

    companion object {
        const val NONCE_HEADER = "X-Morning-Guard-Nonce"
        const val INTEGRITY_HEADER = "X-Morning-Guard-Play-Integrity"
    }
}
