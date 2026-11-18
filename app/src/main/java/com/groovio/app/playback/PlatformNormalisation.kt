package com.groovio.app.playback

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi

/** Session effect, not a custom Media3 renderer/sink. Off means no native effect exists. */
fun createNormalisation(session: Int): SessionEffect {
    if (Build.VERSION.SDK_INT < 28) throw UnsupportedOperationException("Requires Android 9")
    return PlatformNormalisation(session)
}

/** Gentle attenuation-only levelling; no make-up gain or boosting of quiet recordings. */
@RequiresApi(28)
private class PlatformNormalisation(session: Int) : SessionEffect {
    private val effect = DynamicsProcessing(0, session, DynamicsProcessing.Config.Builder(
        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, false, 0, true, 1, false, 0, false,
    ).build())
    override fun configure(levels: List<Int>) {
        effect.setMbcBandAllChannelsTo(0, DynamicsProcessing.MbcBand(
            true, 22000f, 100f, 1500f, 2f, -16f, 6f, -80f, 1f, 0f, 0f,
        ))
        effect.enabled = true
    }
    override fun release() { effect.release() }
}
