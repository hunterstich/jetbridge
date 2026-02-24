package com.hunterstich.idea.jetbridge

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextField
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent


/**
 * A multi-line prompt dialog with macro highlighting.
 *
 * - Enter submits the dialog (OK).
 * - Shift+Enter inserts a newline.
 * - Escape closes the dialog.
 * - Macros like @this, @file, etc. are highlighted as the user types.
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
        editorTextField = EditorTextField(
            /* document = */ document,
            /* project = */ project,
            /* fileType = */ null,
            /* isViewer = */ false,
            /* oneLineMode = */ false
        ).apply {
            preferredSize = Dimension(400, 200)
            setFontInheritedFromLAF(false)
            addSettingsProvider { editor ->
                editor.settings.apply {
                    isLineNumbersShown = false
                    isWhitespacesShown = false
                    isFoldingOutlineShown = false
                    additionalLinesCount = 0
                }
                editor.backgroundColor = EditorColorsManager.getInstance()
                    .globalScheme
                    .defaultBackground
                // Register Enter as an AnAction on the editor component so it runs
                // at the IntelliJ action layer (before the editor's default EnterAction).
                // Shift+Enter is not matched by this shortcut, so it falls through to
                // the default handler which inserts a newline.
                val enterAction = object : DumbAwareAction() {
                    override fun actionPerformed(e: AnActionEvent) {
                        doOKAction()
                    }
                }
                enterAction.registerCustomShortcutSet(
                    CustomShortcutSet.fromString("ENTER"),
                    editor.contentComponent,
                    disposable,
                )
                highlightMacros(editor)
            }
        }

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val editor = editorTextField.editor ?: return
                highlightMacros(editor)
            }
        }, this.disposable)

        init()
    }

    override fun createCenterPanel(): JComponent = editorTextField

    override fun getPreferredFocusedComponent(): JComponent = editorTextField

    /**
     * Scans the editor text for known macros and applies highlight markers.
     */
    private fun highlightMacros(editor: Editor) {
        val markupModel = editor.markupModel
        markupModel.removeAllHighlighters()

        val text = editor.document.text
        val attributes = macroTextAttributes(editor)

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

    private fun macroTextAttributes(editor: Editor): TextAttributes {
        val textColor = editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.LABEL)
        return TextAttributes().apply {
            if (textColor != null) {
                copyFrom(textColor)
            }
            fontType = Font.BOLD
        }
    }
}
