package com.morningguard.data.bible

import com.morningguard.domain.bible.BibleTranslations
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class BibleProxyAuthInterceptor @Inject constructor(
    private val licensedAuth: BibleProxyLicensedAuth,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val translation = request.url.queryParameter("translation")?.trim()?.lowercase() ?: "web"
        val option = BibleTranslations.find(translation)
        if (option?.usesApiBible != true) {
            return chain.proceed(request)
        }

        var authorized = authorize(request)
        repeat(LICENSED_RESPONSE_RETRIES) { attempt ->
            val response = chain.proceed(authorized)
            if (!shouldRetryLicensedResponse(response.code) || attempt == LICENSED_RESPONSE_RETRIES - 1) {
                return response
            }
            response.close()
            licensedAuth.invalidateSession()
            authorized = authorize(request)
        }
        return chain.proceed(authorized)
    }

    private fun shouldRetryLicensedResponse(code: Int): Boolean =
        code == 401 || code == 502 || code == 503 || code == 504

    companion object {
        private const val LICENSED_RESPONSE_RETRIES = 2
    }

    private fun authorize(request: okhttp3.Request): okhttp3.Request {
        val builder = request.newBuilder()
        try {
            licensedAuth.applyLicensedHeaders(builder)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw BibleProxyLicensedAuthException(cause = e)
        }
        return builder.build()
    }
}
