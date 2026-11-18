package com.groovio.app.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/** Conservative live RMS levelling: attenuates loud audio, never boosts quiet passages. */
@UnstableApi
class NormalisingAudioProcessor(private val enabled: () -> Boolean) : BaseAudioProcessor() {
    private var gain = 1.0
    private var rate = 48_000
    private var channels = 2
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        rate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        val out = replaceOutputBuffer(size)
        if (!enabled()) {
            out.put(inputBuffer); out.flip(); gain = 1.0; return
        }
        val scan = inputBuffer.duplicate().order(inputBuffer.order())
        var square = 0.0
        var count = 0
        while (scan.remaining() >= 2) {
            val sample = scan.short / 32768.0
            square += sample * sample; count++
        }
        val rms = sqrt(square / count.coerceAtLeast(1))
        val target = if (rms > 0.0001) min(1.0, 0.125 / rms) else gain
        // Fast attack, slow release; one gain for every channel preserves stereo image.
        val seconds = count.toDouble() / (rate * channels).coerceAtLeast(1)
        val endGain = target + (gain - target) * exp(-seconds / if (target < gain) 0.02 else 3.0)
        var i = 0
        while (inputBuffer.remaining() >= 2) {
            val g = gain + (endGain - gain) * i++ / count.coerceAtLeast(1)
            out.putShort((inputBuffer.short * g).toInt().coerceIn(-32768, 32767).toShort())
        }
        gain = endGain
        out.flip()
    }
    override fun onFlush() { gain = 1.0 }
    override fun onReset() { gain = 1.0 }
}
