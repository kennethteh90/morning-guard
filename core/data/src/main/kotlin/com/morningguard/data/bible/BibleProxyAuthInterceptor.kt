package com.morningguard.data.bible

import com.morningguard.domain.bible.BibleTranslations
import okhttp3.Interceptor
import okhttp3.Response
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
        val builder = request.newBuilder()
        licensedAuth.applyLicensedHeaders(builder)
        return chain.proceed(builder.build())
    }
}
