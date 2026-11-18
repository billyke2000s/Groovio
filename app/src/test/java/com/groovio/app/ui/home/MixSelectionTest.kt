package com.groovio.app.ui.home

import com.groovio.app.data.TrackEntity
import com.groovio.app.online.OnlineTrack
import org.junit.Assert.*
import org.junit.Test

class MixSelectionTest {
    private fun track(id: String) = OnlineTrack(id, id, "Artist", 1000L, "https://art/$id")
    @Test fun `daily shelves contain distinct songs and omit empty shelves`() {
        val mixes = MixSelection.diversify(listOf(
            PersonalMix("a", "A", "Likes", listOf(track("one"), track("two"))),
            PersonalMix("b", "B", "History", listOf(track("two"), track("three"))),
            PersonalMix("c", "C", "History", listOf(track("one"))),
        ))
        assertEquals(listOf("a", "b"), mixes.map { it.id })
        assertEquals(listOf("one", "two", "three"), mixes.flatMap { it.tracks }.map { it.id })
    }
    @Test fun `seeds use listening history and likes with artist diversity`() {
        val recent = listOf(TrackEntity("yt:a", "A", "Artist", null), TrackEntity("yt:b", "B", "Artist", null))
        val liked = listOf(TrackEntity("yt:c", "C", "Other", null), TrackEntity("content://local", "D", "Local", null))
        assertEquals(listOf("yt:a", "yt:c"), MixSelection.seeds(liked, recent).map { it.mediaId })
    }
}
