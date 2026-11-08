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
}
