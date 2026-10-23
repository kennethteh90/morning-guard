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

        val authorized = authorize(request)
        val response = chain.proceed(authorized)
        if (response.code != 401) {
            return response
        }

        // Session may have expired server-side; clear and re-attest once.
        response.close()
        licensedAuth.invalidateSession()
        val retried = authorize(request)
        return chain.proceed(retried)
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
