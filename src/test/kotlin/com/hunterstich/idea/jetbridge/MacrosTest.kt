package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.core.ContextSnapshot
import com.hunterstich.idea.jetbridge.core.cleanAllMacros
import com.hunterstich.idea.jetbridge.core.expandInlineMacros
import org.junit.Test
import kotlin.test.assertEquals


class MacrosTest {

    @Test
    fun testRelativePath_expandsThis() {
        val prompt = "@this hi"
        val providerPath = "/Users/laptop/Code/test/testproject"
        val filePath = "/Users/laptop/Code/test/testproject/README.md"
        val contextSnapshot = ContextSnapshot(
            filePath, filePath, false, 0, 0
        )

        assertEquals("@README.md L0 hi", prompt.expandInlineMacros(providerPath, contextSnapshot))
    }

    @Test
    fun cleanAllMacros_shouldCleanRoutingMacros() {
        val prompt = "@oc:1 @file fix this"
        assertEquals("fix this", prompt.cleanAllMacros())

        val prompt2 = "@gem:my-tmux help"
        assertEquals("help", prompt2.cleanAllMacros())
    }
    }