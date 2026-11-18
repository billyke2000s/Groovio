package com.groovio.app.spoken

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.groovio.app.ui.theme.GroovioColors
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenCardColorTest {
    @Test fun `bright and dark covers retain readable card metadata`() {
        listOf(Color.White, Color.Yellow, Color.Red, Color.Green, Color.Blue, Color.Black).forEach { cover ->
            val background = spokenCardColor(cover)
            assertTrue((GroovioColors.Text.luminance() + 0.05f) / (background.luminance() + 0.05f) >= 4.5f)
        }
    }
}
