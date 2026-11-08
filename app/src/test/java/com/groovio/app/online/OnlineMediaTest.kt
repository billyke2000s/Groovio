package com.groovio.app.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineMediaTest {

    @Test fun `round-trips a video id`() {
        assertEquals("dQw4w9WgXcQ", OnlineMedia.youtubeId(OnlineMedia.uriFor("dQw4w9WgXcQ")))
    }

    @Test fun `rejects other uris`() {
        assertNull(OnlineMedia.youtubeId("content://media/external/audio/1"))
        assertNull(OnlineMedia.youtubeId("groovio://youtube/"))
        assertNull(OnlineMedia.youtubeId("groovio://youtube/abc/def"))
        assertNull(OnlineMedia.youtubeId(null))
    }
}
