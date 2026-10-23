package com.morningguard.app.bible

import com.morningguard.data.bible.BibleProxyAuthInterceptor
import com.morningguard.data.bible.BibleProxyLicensedAuth
import com.morningguard.data.bible.BibleProxyLicensedAuthException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

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
            assertNull(recorded.getHeader(PlayIntegrityBibleProxyLicensedAuth.SESSION_HEADER))
            assertNull(recorded.getHeader("X-Morning-Guard-Token"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `niv chapter request sends session header not legacy token`() {
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
            assertEquals("cached-session", recorded.getHeader(PlayIntegrityBibleProxyLicensedAuth.SESSION_HEADER))
            assertNull(recorded.getHeader("X-Morning-Guard-Token"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `non-IOException from licensed auth becomes BibleProxyLicensedAuthException`() {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(
                    BibleProxyAuthInterceptor(
                        object : BibleProxyLicensedAuth {
                            override fun applyLicensedHeaders(builder: Request.Builder) {
                                throw FakeIntegrityFailure("IntegrityServiceException stand-in")
                            }
                        },
                    ),
                )
                .build()
            val request = Request.Builder()
                .url(server.url("/v1/chapter?translation=nkjv&book=John&chapter=1"))
                .build()
            try {
                client.newCall(request).execute().close()
                fail("expected IOException")
            } catch (e: BibleProxyLicensedAuthException) {
                assertTrue(e.message!!.contains("licensed Bible translation"))
                assertTrue(e.cause is FakeIntegrityFailure)
            } catch (e: IOException) {
                // OkHttp may wrap; still must be an IOException subclass path.
                assertTrue(e is BibleProxyLicensedAuthException || e.cause is BibleProxyLicensedAuthException)
            }
        } finally {
            server.shutdown()
        }
    }

    private class FakeIntegrityFailure(message: String) : Exception(message)

    @Test
    fun `401 invalidates session and retries once`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
        val auth = RecordingLicensedAuth()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(BibleProxyAuthInterceptor(auth))
                .build()
            val request = Request.Builder()
                .url(server.url("/v1/chapter?translation=nkjv&book=John&chapter=1"))
                .build()
            val response = client.newCall(request).execute()
            assertEquals(200, response.code)
            response.close()
            assertEquals(1, auth.invalidations.get())
            assertEquals(2, auth.applyCount.get())
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private class RecordingLicensedAuth : BibleProxyLicensedAuth {
        var applied = false
        val applyCount = AtomicInteger(0)
        val invalidations = AtomicInteger(0)

        override fun applyLicensedHeaders(builder: Request.Builder) {
            applied = true
            applyCount.incrementAndGet()
            builder.header(PlayIntegrityBibleProxyLicensedAuth.SESSION_HEADER, "cached-session")
        }

        override fun invalidateSession() {
            invalidations.incrementAndGet()
        }
    }
}
