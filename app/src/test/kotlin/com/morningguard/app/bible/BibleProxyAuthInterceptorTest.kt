package com.morningguard.app.bible

import com.morningguard.data.bible.BibleProxyAuthInterceptor
import com.morningguard.data.bible.BibleProxyLicensedAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleProxyAuthInterceptorTest {

    @Test
    fun `web chapter request sends no licensed headers`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(BibleProxyAuthInterceptor(RecordingLicensedAuth()))
                .build()
            val request = Request.Builder()
                .url(server.url("/v1/chapter?translation=web&book=John&chapter=3"))
                .build()
            client.newCall(request).execute().close()
            val recorded = server.takeRequest()
            assertNull(recorded.getHeader(PlayIntegrityBibleProxyLicensedAuth.NONCE_HEADER))
            assertNull(recorded.getHeader(PlayIntegrityBibleProxyLicensedAuth.INTEGRITY_HEADER))
            assertNull(recorded.getHeader("X-Morning-Guard-Token"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `niv chapter request sends integrity headers not legacy token`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
        val recording = RecordingLicensedAuth()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(BibleProxyAuthInterceptor(recording))
                .build()
            val request = Request.Builder()
                .url(server.url("/v1/chapter?translation=niv&book=Genesis&chapter=1"))
                .build()
            client.newCall(request).execute().close()
            val recorded = server.takeRequest()
            assertTrue(recording.applied)
            assertTrue(!recorded.getHeader(PlayIntegrityBibleProxyLicensedAuth.NONCE_HEADER).isNullOrBlank())
            assertTrue(!recorded.getHeader(PlayIntegrityBibleProxyLicensedAuth.INTEGRITY_HEADER).isNullOrBlank())
            assertNull(recorded.getHeader("X-Morning-Guard-Token"))
        } finally {
            server.shutdown()
        }
    }

    private class RecordingLicensedAuth : BibleProxyLicensedAuth {
        var applied = false
        override fun applyLicensedHeaders(builder: Request.Builder) {
            applied = true
            builder.header(PlayIntegrityBibleProxyLicensedAuth.NONCE_HEADER, "nonce")
            builder.header(PlayIntegrityBibleProxyLicensedAuth.INTEGRITY_HEADER, "token")
        }
    }
}
