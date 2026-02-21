package com.hunterstich.idea.jetbridge

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.vfs.VirtualFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals


class MacrosTest {

    lateinit var mockEditor: Editor
    lateinit var mockVirtualFile: VirtualFile
    lateinit var mockCaretModel: CaretModel
    lateinit var mockCaret: Caret
    private lateinit var mockApplication: Application

    @Before
    fun setUp() {
        // Mock ApplicationManager to avoid NullPointerException in unit tests
        mockApplication = mock {
            doAnswer { invocation -> invocation.getArgument<Runnable>(0).run() }
                .whenever(it)
                .runReadAction(any<Runnable>())
        }
        ApplicationManager.setApplication(mockApplication) { }
    }

    @After
    fun tearDown() {
        // Clean up the mocked application
    }

    /**
     * Sets up the mock editor with default values.
     * Tests can customize individual mock methods using `whenever(...).thenReturn(...)`.
     */
    fun setupMockEditor(
        filePath: String = "",
        hasSelection: Boolean = false,
        startLine: Int = 0,
        startColumn: Int = 0,
        endLine: Int = 0,
        endColumn: Int = 0
    ) {
        mockVirtualFile = mock {
            whenever(it.path).thenReturn(filePath)
        }

        mockCaret = mock {
            whenever(it.hasSelection()).thenReturn(hasSelection)
            whenever(it.selectionStartPosition).thenReturn(VisualPosition(startLine, startColumn))
            whenever(it.selectionEndPosition).thenReturn(VisualPosition(endLine, endColumn))
        }

        mockCaretModel = mock {
            whenever(it.primaryCaret).thenReturn(mockCaret)
        }

        mockEditor = mock {
            whenever(it.virtualFile).thenReturn(mockVirtualFile)
            whenever(it.caretModel).thenReturn(mockCaretModel)
        }
    }

    @Test
    fun testRelativePath_expandsThis() {
        val prompt = "@this hi"
        val providerPath = "/Users/laptop/Code/test/testproject"
        
        setupMockEditor(
            filePath = "/Users/laptop/Code/test/testproject/README.md",
            hasSelection = false,
            startLine = 0,
        )

        assertEquals("@README.md L0 hi", prompt.expandInlineMacros(mockEditor, providerPath))
    }

    @Test
    fun cleanAllMacros_shouldNotAffectSourceAtSign() {
        val prompt = "@README.md"
        assertEquals("@README.md", prompt.cleanAllMacros())
    }
}