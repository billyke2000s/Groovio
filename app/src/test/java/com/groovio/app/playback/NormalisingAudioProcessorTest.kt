package com.groovio.app.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@androidx.media3.common.util.UnstableApi
class NormalisingAudioProcessorTest {
    private fun processor(on: Boolean) = NormalisingAudioProcessor { on }.apply {
        configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); flush()
    }
    private fun input(value: Short) = ByteBuffer.allocateDirect(19200).order(ByteOrder.nativeOrder()).apply {
        repeat(9600) { putShort(value) }; flip()
    }
    @Test fun disabledPreservesSamples() {
        val p = processor(false)
        p.queueInput(input(12000))
        val out = p.output.order(ByteOrder.nativeOrder())
        while (out.hasRemaining()) assertEquals(12000.toShort(), out.short)
    }
    @Test fun loudAudioIsReducedWithoutClipping() {
        val p = processor(true)
        repeat(20) { p.queueInput(input(30000)); p.output }
        p.queueInput(input(30000))
        val out = p.output.order(ByteOrder.nativeOrder())
        while (out.hasRemaining()) assertTrue(abs(out.short.toInt()) <= 4200)
    }
    @Test fun quietAudioIsNeverBoosted() {
        val p = processor(true)
        p.queueInput(input(1000))
        val out = p.output.order(ByteOrder.nativeOrder())
        while (out.hasRemaining()) assertEquals(1000.toShort(), out.short)
    }
}
