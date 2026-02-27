package com.hunterstich.idea.jetbridge

import com.intellij.ide.plugins.PluginManagerCore
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
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorTextField
import com.maddyhome.idea.vim.KeyHandler
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.helper.inNormalMode
import com.maddyhome.idea.vim.newapi.ij
import com.maddyhome.idea.vim.newapi.vim
import java.awt.Dimension
import java.awt.Font
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke


/**
 * A multi-line prompt dialog with macro highlighting.
 *
 * - Enter submits the dialog (OK).
 * - Shift+Enter inserts a newline.
 * - Escape closes the dialog.
 * - Macros like @this, @file, etc. are highlighted as the user types.
 * - IdeaVIM keybindings work if the user has `set ideavimsupport+=dialog` in their .ideavimrc.
 */
class PromptDialog(
    project: Project?,
    dialogTitle: String,
    private val prepopulatedText: String,
) : DialogWrapper(project) {

    private val editorTextField: EditorTextField

    val vimPlugin = PluginManagerCore.getPlugin(PluginId.getId("IdeaVIM"))
    val vimEnabled = vimPlugin != null && vimPlugin.isEnabled()

    val inputText: String
        get() = editorTextField.text

    init {
        title = dialogTitle
        val document = EditorFactory.getInstance().createDocument(prepopulatedText)
        if (vimEnabled) {
            // Hack to force vim being enabled by getting around
            // com.maddyhome.idea.vim.helper.EditorHelper#Editor.isNotFileEditorExceptAllowed()
            // which disables vim in tool windows and dialogs except for a few hard-coded exceptions
            val virtualFile = LightVirtualFile("Dummy.txt", prepopulatedText)
            FileDocumentManagerImpl.registerDocument(document, virtualFile)
        }
        editorTextField = EditorTextField(
            /* document = */ document,
            /* project = */ project,
            /* fileType = */ null,
            /* isViewer = */ false,
            /* oneLineMode = */ false
        ).apply {
            preferredSize = Dimension(450, 150)
            setFontInheritedFromLAF(false)
            addSettingsProvider { editor ->
                editor.settings.apply {
                    isLineNumbersShown = false
                    isWhitespacesShown = false
                    isFoldingOutlineShown = false
                    additionalLinesCount = 0
                    isUseSoftWraps = true
                    // Don't draw the little return icons
                    isPaintSoftWraps = false
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

        registerVimCommandShortcuts()

        init()
    }

    override fun createCenterPanel(): JComponent = editorTextField

    override fun getPreferredFocusedComponent(): JComponent = editorTextField

    override fun createCancelAction(): ActionListener {
        return ActionListener { e ->
            if (!maybeHandleVimEscape()) {
                doCancelAction(e)
            }
        }
    }

    /**
     * If IdeaVIM is not in normal mode, intercept the cancel action and send the escape key to
     * IdeaVIM which will put it in normal mode.
     *
     * This is a fix. When you open the dialog, vim is in insert mode. Pressing escape before
     * moving the caret would cause the dialog to cancel instead of being handled by IdeaVIM.
     */
    private fun maybeHandleVimEscape(): Boolean {
        try {
            if (!vimEnabled) return false
            val editor = editorTextField.editor ?: return false
            val vim = editor.vim
            if (vim.mode.inNormalMode) return false

            val context = injector.executionContextManager.getEditorExecutionContext(vim)
            KeyHandler.getInstance().handleKey(
                vim,
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                context,
                KeyHandler.getInstance().keyHandlerState
            )
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /** Handle vim commands - `:wq` to submit the prompt and `q` to cancel the dialog. */
    private fun registerVimCommandShortcuts() {
        if (!vimEnabled) return

        val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val keyEventDispatcher = KeyEventDispatcher { event ->
            if (event.id != KeyEvent.KEY_PRESSED || event.keyCode != KeyEvent.VK_ENTER) {
                return@KeyEventDispatcher false
            }

            val editor = editorTextField.editor ?: return@KeyEventDispatcher false
            val commandLine = injector.commandLine.getActiveCommandLine()
                ?: return@KeyEventDispatcher false
            if (commandLine.getLabel() != ":") return@KeyEventDispatcher false
            if (commandLine.editor.ij != editor) return@KeyEventDispatcher false

            when (commandLine.text.trim().lowercase()) {
                "q" -> {
                    commandLine.close(refocusOwningEditor = false, resetCaret = false)
                    doCancelAction()
                    true
                }

                "wq" -> {
                    commandLine.close(refocusOwningEditor = false, resetCaret = false)
                    doOKAction()
                    true
                }

                else -> false
            }
        }

        keyboardFocusManager.addKeyEventDispatcher(keyEventDispatcher)
        Disposer.register(disposable) {
            keyboardFocusManager.removeKeyEventDispatcher(keyEventDispatcher)
        }
    }

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
