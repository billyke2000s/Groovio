package com.groovio.app.online

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class StreamProbeTest {
    private lateinit var server: MockWebServer
    private val checker = StreamProbe(OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build())

    @Before fun start() {
        server = MockWebServer()
        server.start()
    }
    @After fun stop() { server.shutdown() }

    private fun stream() = ResolvedAudio(
        server.url("/recording").toString(), "opus", 128000,
        mapOf("User-Agent" to "Groovio-Playback-Test"), 0L,
    )

    @Test fun `checks real bytes at start and later seek with matching headers`() {
        server.enqueue(MockResponse().setResponseCode(206).setBody("first-range"))
        server.enqueue(MockResponse().setResponseCode(206).setBody("later-range"))
        assertTrue(checker.verify(stream()).playable)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("GET", first.method)
        assertEquals("bytes=0-1023", first.getHeader("Range"))
        assertEquals("bytes=3145728-3146751", second.getHeader("Range"))
        assertEquals("Groovio-Playback-Test", first.getHeader("User-Agent"))
        assertEquals("Groovio-Playback-Test", second.getHeader("User-Agent"))
    }

    @Test fun `rejects a media URL whose first byte range returns 403`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        val result = checker.verify(stream())
        assertFalse(result.playable)
        assertEquals(403, result.httpStatus)
        assertEquals(0L, result.failedOffset)
        assertEquals(1, server.requestCount)
    }

    @Test fun `rejects token-gated media which works initially but returns 403 after seeking`() {
        server.enqueue(MockResponse().setResponseCode(206).setBody("first-range"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        val result = checker.verify(stream())
        assertFalse(result.playable)
        assertEquals(403, result.httpStatus)
        assertEquals(3_145_728L, result.failedOffset)
        assertEquals(2, server.requestCount)
    }

    @Test fun `rejects later range when host ignores range and responds 200`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("start"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ignored-range"))
        val result = checker.verify(stream())
        assertFalse(result.playable)
        assertEquals(200, result.httpStatus)
        assertEquals(3_145_728L, result.failedOffset)
    }

    @Test fun `selector really switches to next URL after HTTP 403 and never reprobes rejected URL`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("rejected"))
        server.enqueue(MockResponse().setResponseCode(206).setBody("good-start"))
        server.enqueue(MockResponse().setResponseCode(206).setBody("good-seek"))
        val bad = stream().copy(url = server.url("/bad").toString())
        val good = stream().copy(url = server.url("/good").toString())
        val rejected = mutableSetOf<String>()
        val selector = SpokenSourceSelector(checker::verify, rejected)

        assertEquals(null, selector.tryCandidate(bad))
        assertEquals(403, selector.lastHttpCode)
        assertTrue(bad.url in rejected)
        assertEquals(null, selector.tryCandidate(bad))
        assertEquals(good, selector.tryCandidate(good))
        assertEquals(3, server.requestCount)
        assertEquals("/bad", server.takeRequest().path)
        assertEquals("/good", server.takeRequest().path)
        assertEquals("/good", server.takeRequest().path)
    }

    @Test fun `rejects a successful HTTP response with empty media body`() {
        server.enqueue(MockResponse().setResponseCode(206).setBody(""))
        assertFalse(checker.verify(stream()).playable)
    }
}
