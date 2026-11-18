package com.groovio.app.online

import org.junit.Assert.*
import org.junit.Test

class ChartDiscoveryTest {
    @Test fun publishedChartOrderAndMetadataArePreserved() {
        val chart = ChartDiscovery.parse("""{"feed":{"entry":[{"im:name":{"label":"First"},"im:artist":{"label":"Artist A"}},{"im:name":{"label":"Second"},"im:artist":{"label":"Artist B"}}]}}""")
        assertEquals(listOf("First", "Second"), chart.tracks.map { it.title })
        assertEquals("Artist A", chart.tracks.first().artist)
        assertNull(chart.tracks.first().uri)
    }
}
