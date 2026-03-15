package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.core.parsePaneLine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeminiCliProviderTest {

    @Test
    fun parsePaneLineValid() {
        val line = "jetbridge-gemini:1.0|jetbridge-gemini|gemini-1|1|0"
        val result = parsePaneLine(line)
        assertNotNull(result)
        assertEquals("jetbridge-gemini:1.0", result.tmuxId)
        assertEquals("jetbridge-gemini", result.sessionName)
        assertEquals("gemini-1", result.windowName)
        assertEquals("1", result.windowIndex)
        assertEquals("0", result.paneIndex)
    }

    @Test
    fun parsePaneLineMalformed() {
        assertNull(parsePaneLine(""))
        assertNull(parsePaneLine("only|three|parts"))
        assertNull(parsePaneLine("a|b|c|d"))
    }

    @Test
    fun parsePaneLineWindowNameContainsGem() {
        val line = "mysession:0.0|mysession|gem-work|0|0"
        val result = parsePaneLine(line)
        assertNotNull(result)
        assertTrue(result.windowName.contains("gem", ignoreCase = true))
    }

    @Test
    fun parsePaneLineWindowNameContainsGemini() {
        val line = "mysession:0.0|mysession|gemini-5|0|0"
        val result = parsePaneLine(line)
        assertNotNull(result)
        assertTrue(result.windowName.contains("gem", ignoreCase = true))
    }

    @Test
    fun parsePaneLineWindowNameNoGem() {
        val line = "mysession:0.0|mysession|zsh|0|0"
        val result = parsePaneLine(line)
        assertNotNull(result)
        assertTrue(!result.windowName.contains("gem", ignoreCase = true))
    }

    @Test
    fun parsePaneLineCaseInsensitive() {
        val line = "mysession:0.0|mysession|GEM-WORK|0|0"
        val result = parsePaneLine(line)
        assertNotNull(result)
        assertTrue(result.windowName.contains("gem", ignoreCase = true))
    }
}
