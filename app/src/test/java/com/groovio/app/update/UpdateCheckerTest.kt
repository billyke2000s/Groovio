package com.groovio.app.update

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    private fun release(tag: String, apk: Boolean = true, draft: Boolean = false) = """
        {"tag_name":"$tag","draft":$draft,"html_url":"https://github.com/x/r/$tag",
         "assets":[${if (apk) """{"name":"groovio-$tag.apk","browser_download_url":"https://dl/$tag.apk"}""" else ""}]}
    """

    private fun parse(vararg r: String) = JsonParser.array().from("[${r.joinToString(",")}]")

    @Test fun `parses build numbers from tags`() {
        assertEquals(42, UpdateChecker.buildNumber("v0.1.42"))
        assertEquals(7, UpdateChecker.buildNumber("0.1.7"))
        assertNull(UpdateChecker.buildNumber("v0.1.0-test.1"))
    }

    @Test fun `picks the newest release above the current build`() {
        val list = parse(release("v0.1.40"), release("v0.1.45"), release("v0.1.43"))
        val u = UpdateChecker.newest(list, currentBuild = 41)
        assertEquals("v0.1.45", u?.version)
        assertEquals("https://dl/v0.1.45.apk", u?.apkUrl)
    }

    @Test fun `no update when current is newest`() {
        assertNull(UpdateChecker.newest(parse(release("v0.1.40")), currentBuild = 40))
    }

    @Test fun `skips drafts and releases without an apk`() {
        val list = parse(release("v0.1.50", draft = true), release("v0.1.49", apk = false), release("v0.1.48"))
        assertEquals("v0.1.48", UpdateChecker.newest(list, currentBuild = 1)?.version)
    }
    @Test fun `stable excludes prereleases even with numeric tags`() {
        val preview = release("v0.1.99").replace("\"draft\":false", "\"draft\":false,\"prerelease\":true")
        assertNull(UpdateChecker.newest(parse(preview), 71))
    }

    @Test fun `experimental requires consent and chooses higher build`() {
        val list = parse(release("experimental-v0.1.80"), release("v0.1.72"), release("experimental-baseline-v0.1.71"))
        assertEquals("v0.1.72", UpdateChecker.newest(list, 71)?.version)
        assertEquals("experimental-v0.1.80", UpdateChecker.newest(list, 71, experimental = true)?.version)
        assertNull(UpdateChecker.newest(list, 80, experimental = true))
    }
    @Test fun parsesWhatsNewForTheChosenRelease() {
        val entry = """{"tag_name":"experimental-v0.1.105","draft":false,"prerelease":true,
            "html_url":"https://github.com/x/r/releases/tag/experimental-v0.1.105",
            "body":"## What's new\n\n- Add an update changelog\n- Fix downloads\n\n## Notes\nOther text",
            "assets":[{"name":"groovio.apk","browser_download_url":"https://dl/groovio.apk"}]}"""
        val update = UpdateChecker.newest(parse(entry), 100, experimental = true)
        assertEquals("• Add an update changelog\n• Fix downloads", update?.changes)
    }

    @Test fun ignoresLegacyGenericNotes() {
        assertNull(UpdateChecker.changesFrom("# Groovio Android release\nGeneric notes"))
        assertNull(UpdateChecker.changesFrom(null))
        assertNull(UpdateChecker.newest(parse(release("v0.1.90")), 71)?.changes)
    }

    @Test fun limitsWhatsNewToEightItems() {
        val body = "## What's new\n" + (1..12).joinToString("\n") { "- Change $it" } +
            "\n\n## Notes\n- Unrelated"
        val changes = UpdateChecker.changesFrom(body)
        assertEquals(8, changes?.lines()?.size)
        assertEquals(false, changes?.contains("Unrelated"))
    }
}
