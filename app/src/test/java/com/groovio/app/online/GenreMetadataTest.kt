package com.groovio.app.online

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenreMetadataTest {
    @Test fun `genre uses exact artist identity and caches actual metadata`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("artist_genres", Context.MODE_PRIVATE).edit().clear().commit()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"artists":[{"name":"Artist","score":100,"id":"12345678-1234-1234-1234-123456789abc"}]}"""))
            server.enqueue(MockResponse().setBody("""{"genres":[{"name":"jazz","count":8},{"name":"blues","count":2}]}"""))
            val metadata = GenreMetadata(context, endpoint = server.url("/ws/2/").toString(), intervalMs = 0)
            assertEquals("jazz", metadata.genre("Artist"))
            assertEquals("jazz", metadata.genre("Artist"))
            assertEquals(2, server.requestCount)
            assertTrue(server.takeRequest().getHeader("User-Agent")!!.startsWith("Groovio/"))
        }
    }
    @Test fun `ambiguous artist names never acquire a guessed genre`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("artist_genres", Context.MODE_PRIVATE).edit().clear().commit()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"artists":[{"name":"Ambiguous","score":100},{"name":"Ambiguous","score":100}]}"""))
            val metadata = GenreMetadata(context, endpoint = server.url("/ws/2/").toString(), intervalMs = 0)
            assertNull(metadata.genre("Ambiguous"))
            assertEquals(1, server.requestCount)
        }
    }
}
