package com.groovio.app.offline

import com.groovio.app.online.ResolvedAudio
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class AudioTransferTest {
    @Test fun partialResponsesAreCombinedIntoTheWholeSong() = runBlocking {
        val server = MockWebServer()
        val file = File.createTempFile("groovio", ".audio")
        try {
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/8").setBody("abcd"))
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 4-7/8").setBody("efgh"))
            val progress = mutableListOf<Int>()
            AudioTransfer(OkHttpClient(), chunkBytes = 4).save(ResolvedAudio(server.url("/audio").toString(), null, 0, emptyMap(), 0), file, { false }) { progress.add(it) }
            assertEquals("abcdefgh", file.readText())
            assertEquals("bytes=0-3", server.takeRequest().getHeader("Range"))
            assertEquals("bytes=4-7", server.takeRequest().getHeader("Range"))
            assertEquals(100, progress.last())
        } finally { file.delete(); server.shutdown() }
    }
    @Test fun incorrectContinuationIsRejected() = runBlocking {
        val server = MockWebServer()
        val file = File.createTempFile("groovio", ".audio")
        try {
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/8").setBody("abcd"))
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/8").setBody("abcd"))
            var failed = false
            try { AudioTransfer(OkHttpClient(), chunkBytes = 4).save(ResolvedAudio(server.url("/audio").toString(), null, 0, emptyMap(), 0), file, { false }) {} }
            catch (_: IOException) { failed = true }
            assertTrue(failed)
        } finally { file.delete(); server.shutdown() }
    }
    @Test fun acceptsCompleteResponseWhenServerIgnoresInitialRange() = runBlocking {
        val server = MockWebServer()
        val file = File.createTempFile("groovio", ".audio")
        try {
            server.enqueue(MockResponse().setBody("complete song"))
            AudioTransfer(OkHttpClient()).save(ResolvedAudio(server.url("/audio").toString(), null, 0, emptyMap(), 0), file, { false }) {}
            assertEquals("complete song", file.readText())
        } finally { file.delete(); server.shutdown() }
    }
}
