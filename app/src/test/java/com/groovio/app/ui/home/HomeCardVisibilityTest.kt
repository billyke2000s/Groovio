package com.groovio.app.ui.home

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeCardVisibilityTest {
    @Test fun hiddenItemsAreSectionScopedAndSurviveReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("home_card_visibility", Context.MODE_PRIVATE).edit().clear().commit()
        val store = HomeCardVisibility(context)
        store.hide("quick", "playlist:5")
        assertFalse(HomeCardVisibility.visible(store.hidden.value, "quick", "playlist:5"))
        assertTrue(HomeCardVisibility.visible(store.hidden.value, "playlists", "playlist:5"))
        assertTrue(HomeCardVisibility.visible(store.hidden.value, "quick", "playlist:6"))
        assertFalse(HomeCardVisibility.visible(HomeCardVisibility(context).hidden.value, "quick", "playlist:5"))
        context.getSharedPreferences("home_card_visibility", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
