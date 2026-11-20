package com.groovio.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsPersistenceTest {
    @Test fun `audio settings persist and backup never opts a stable user into experimental`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("groovio_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = GroovioSettings(context)
        assertFalse(settings.normalise.value)
        assertFalse(settings.equalizerEnabled.value)
        assertFalse(settings.experimentalUpdates.value)
        settings.setEqualizerEnabled(true)
        settings.setEqualizerBands(listOf(6, 3, 0, -3, -6))
        settings.setNormalise(true)
        settings.setExperimentalUpdates(true)
        val recreated = GroovioSettings(context)
        assertTrue(recreated.normalise.value)
        assertEquals(listOf(6, 3, 0, -3, -6), recreated.equalizerBands.value)
        val backup = recreated.export()
        settings.setExperimentalUpdates(false)
        settings.restore(backup.put("experimental_updates", true))
        assertFalse(settings.experimentalUpdates.value)
        assertFalse(backup.has("experimentalUpdates"))
    }
    @Test fun `landscape video is disabled and mobile high-res is opt-in`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("groovio_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val fresh = GroovioSettings(context)
        assertEquals("off", fresh.landscapeVideoMode.value)
        assertFalse(fresh.highResolutionMobileArtwork.value)
        fresh.setLandscapeVideoMode("wifi")
        fresh.setHighResolutionMobileArtwork(true)
        val persisted = GroovioSettings(context)
        assertEquals("wifi", persisted.landscapeVideoMode.value)
        assertTrue(persisted.highResolutionMobileArtwork.value)
        assertEquals("wifi", persisted.export().getString("landscapeVideoMode"))
        assertTrue(persisted.export().getBoolean("highResolutionMobileArtwork"))
        persisted.restore(JSONObject().put("landscapeVideoMode", "unknown"))
        assertEquals("off", persisted.landscapeVideoMode.value)
        assertFalse(persisted.highResolutionMobileArtwork.value)
    }
}
