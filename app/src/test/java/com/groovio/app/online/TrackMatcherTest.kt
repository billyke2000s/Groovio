package com.groovio.app.online

import com.groovio.app.online.TrackMatcher.Candidate
import com.groovio.app.online.TrackMatcher.Wanted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    private val getLucky = Wanted("Get Lucky", "Daft Punk, Pharrell Williams", 248_000, "Random Access Memories")

    @Test fun `official topic upload scores as a good match`() {
        val official = Candidate("Get Lucky", "Daft Punk - Topic", 248_000)
        assertTrue(TrackMatcher.score(getLucky, official) >= TrackMatcher.GOOD_MATCH)
    }

    @Test fun `original beats sped up, cover and live versions`() {
        val candidates = listOf(
            Candidate("Get Lucky (Sped Up)", "Daft Punk - Topic", 190_000),
            Candidate("Get Lucky - Cover", "Some Singer", 245_000),
            Candidate("Get Lucky (Live)", "Daft Punk", 300_000),
            Candidate("Get Lucky", "Daft Punk - Topic", 249_000),
        )
        assertEquals(3, TrackMatcher.best(getLucky, candidates)?.first)
    }

    @Test fun `alt-version words only penalise when the wanted title lacks them`() {
        val wantRemix = Wanted("Get Lucky (Remix)", "Daft Punk", null)
        val remix = Candidate("Get Lucky (Remix)", "Daft Punk", null)
        val plain = Candidate("Get Lucky", "Daft Punk", null)
        // Wanting the remix: the remix is not penalised and beats the plain version.
        assertEquals(70.0, TrackMatcher.score(wantRemix, remix), 0.001)
        assertTrue(TrackMatcher.score(wantRemix, remix) > TrackMatcher.score(wantRemix, plain))
        // Wanting the original: the remix loses 40.
        assertEquals(30.0, TrackMatcher.score(getLucky, remix), 0.001)
    }

    @Test fun `live is matched as a whole word only`() {
        val want = Wanted("Alive", "Daft Punk", null)
        val r = Candidate("Alive", "Daft Punk", null)
        // "alive" must not be treated as the "live" alt-version.
        assertTrue(TrackMatcher.score(want, r) >= 70)
    }

    @Test fun `length far off is penalised`() {
        val near = Candidate("Get Lucky", "Daft Punk", 250_000)
        val far = Candidate("Get Lucky", "Daft Punk", 600_000)
        assertTrue(TrackMatcher.score(getLucky, near) - TrackMatcher.score(getLucky, far) >= 50)
    }

    @Test fun `accents and case are normalised`() {
        assertEquals("beyonce", TrackMatcher.compact("Beyoncé"))
        assertEquals(listOf("sigur", "ros"), TrackMatcher.words("Sigur Rós!"))
    }

    @Test fun `album query drops filler words`() {
        assertEquals("catherine full body", TrackMatcher.albumQuery("Catherine & Catherine Full Body Soundtrack Set"))
    }

    @Test fun `livestreams are penalised`() {
        val live = Candidate("Get Lucky", "Daft Punk - Topic", 248_000, isLive = true)
        val normal = Candidate("Get Lucky", "Daft Punk - Topic", 248_000)
        assertEquals(50.0, TrackMatcher.score(getLucky, normal) - TrackMatcher.score(getLucky, live), 0.001)
    }
}
