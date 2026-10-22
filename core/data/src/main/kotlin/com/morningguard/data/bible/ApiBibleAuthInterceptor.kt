package com.morningguard.data.bible

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class ApiBibleAuthInterceptor @Inject constructor(
    private val config: ApiBibleConfig,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (config.apiKey.isBlank()) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("api-key", config.apiKey)
                .build(),
        )
    }
}
