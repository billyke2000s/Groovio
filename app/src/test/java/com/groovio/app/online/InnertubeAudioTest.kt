package com.groovio.app.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InnertubeAudioTest {
    @Test fun `reads codec from mime type`() {
        assertEquals("opus", InnertubeAudio.codecOf("audio/webm; codecs=\"opus\""))
        assertEquals("mp4a.40.2", InnertubeAudio.codecOf("audio/mp4; codecs=\"mp4a.40.2\""))
        assertNull(InnertubeAudio.codecOf("audio/mp4"))
    }

    @Test fun `parses a signatureCipher`() {
        val q = InnertubeAudio.parseQuery("s=AB%3DC&sp=sig&url=https%3A%2F%2Fr1.googlevideo.com%2Fvideoplayback%3Fid%3D1")
        assertEquals("AB=C", q["s"])
        assertEquals("sig", q["sp"])
        assertEquals("https://r1.googlevideo.com/videoplayback?id=1", q["url"])
    }

    @Test fun `tries the bot's TV client first`() {
        assertEquals(listOf("TVHTML5", "ANDROID_VR"), InnertubeAudio.CLIENTS.map { it.name })
        assertEquals("Samsung", InnertubeAudio.TV.extraClientFields["deviceMake"])
    }
}
