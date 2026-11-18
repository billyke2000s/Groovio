package com.groovio.app.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class PublicMusicFolderTest {
    @Test fun keepsRealAudioExtensions() {
        val file = File.createTempFile("groovio-format", ".bin")
        try {
            file.writeBytes("OggS0123456789".toByteArray())
            assertEquals("ogg" to "audio/ogg", PublicMusicFolder.formatFor(file, "opus"))
            file.writeBytes(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte(), 1))
            assertEquals("webm" to "audio/webm", PublicMusicFolder.formatFor(file, "opus"))
            file.writeBytes("ID3test".toByteArray())
            assertEquals("mp3" to "audio/mpeg", PublicMusicFolder.formatFor(file))
            file.writeBytes("1234ftypM4A".toByteArray())
            assertEquals("m4a" to "audio/mp4", PublicMusicFolder.formatFor(file))
        } finally { file.delete() }
    }
    @Test fun sanitizePublicFilename() {
        assertEquals("A - B", PublicMusicFolder.cleanName("A / B"))
        assertFalse(PublicMusicFolder.cleanName("???").contains('?'))
    }
}
