package com.hunterstich.idea.jetbridge

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextField
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent

/**
 * A multi-line prompt dialog with macro highlighting.
 *
 * - Enter submits the dialog (OK).
 * - Shift+Enter inserts a newline.
 * - Macros like @this, @file, etc. are highlighted as the user types.
 * - IdeaVIM keybindings work automatically if the plugin is installed and
 *   the user has enabled `set ideavimsupport+=dialog` in their .ideavimrc.
 */
class JetbridgeDialog(
    project: Project?,
    dialogTitle: String,
    private val prepopulatedText: String,
) : DialogWrapper(project) {

    private val editorTextField: EditorTextField

    val inputText: String
        get() = editorTextField.text

    init {
        title = dialogTitle
        val document = EditorFactory.getInstance().createDocument(prepopulatedText)
        editorTextField = EditorTextField(document, project, null, false, true).apply {
            preferredSize = Dimension(400, 200)
            addSettingsProvider { editor ->
                editor.settings.apply {
                    isLineNumbersShown = false
                    isWhitespacesShown = false
                    isFoldingOutlineShown = false
                    additionalLinesCount = 0
                }
                editor.contentComponent.addKeyListener(EnterKeyListener())
                highlightMacros(editor)
            }
        }

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val editor = editorTextField.editor ?: return
                highlightMacros(editor)
            }
        })

        init()
    }

    override fun createCenterPanel(): JComponent = editorTextField

    override fun getPreferredFocusedComponent(): JComponent = editorTextField

    /**
     * Scans the editor text for known macros and applies highlight markers.
     */
    private fun highlightMacros(editor: com.intellij.openapi.editor.Editor) {
        val markupModel = editor.markupModel
        markupModel.removeAllHighlighters()

        val text = editor.document.text
        val attributes = macroTextAttributes()

        for (macro in allMacros) {
            var startIndex = text.indexOf(macro)
            while (startIndex >= 0) {
                markupModel.addRangeHighlighter(
                    startIndex,
                    startIndex + macro.length,
                    HighlighterLayer.SELECTION - 1,
                    attributes,
                    HighlighterTargetArea.EXACT_RANGE,
                )
                startIndex = text.indexOf(macro, startIndex + macro.length)
            }
        }

        for (regex in allMacroRegex) {
            for (match in regex.findAll(text)) {
                markupModel.addRangeHighlighter(
                    match.range.first,
                    match.range.last + 1,
                    HighlighterLayer.SELECTION - 1,
                    attributes,
                    HighlighterTargetArea.EXACT_RANGE,
                )
            }
        }
    }

    private fun macroTextAttributes(): TextAttributes {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val textColor = scheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)
        return TextAttributes().apply {
            if (textColor != null) {
                copyFrom(textColor)
            }
            fontType = Font.BOLD
        }
    }

    /**
     * Intercepts Enter to submit the dialog. Shift+Enter inserts a newline.
     */
    private inner class EnterKeyListener : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                e.consume()
                doOKAction()
            }
        }
    }
}
