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
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextField
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke


/**
 * A multi-line prompt dialog with macro highlighting.
 *
 * - Enter submits the dialog (OK).
 * - Shift+Enter inserts a newline.
 * - Macros like @this, @file, etc. are highlighted as the user types.
 * - Escape switches from insert to normal mode when IdeaVIM is active; a
 *   second Escape (in normal mode) closes the dialog.
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
                // Register Escape at the IntelliJ action layer so it fires even when
                // IdeEventQueue has consumed the underlying KeyEvent (which prevents the
                // DialogWrapper's WHEN_IN_FOCUSED_WINDOW Swing input map from firing).
                // When IdeaVIM is in insert mode, forward Escape through IdeaVIM's
                // KeyHandler to switch to normal mode and keep the dialog open.
                // When already in normal mode (or IdeaVIM isn't installed), close the dialog.
                val escapeAction = object : DumbAwareAction() {
                    override fun actionPerformed(e: AnActionEvent) {
                        if (!tryHandleVimEscape()) {
                            doCancelAction(e.inputEvent)
                        }
                    }
                }
                escapeAction.registerCustomShortcutSet(
                    CustomShortcutSet.fromString("ESCAPE"),
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
        })

        init()
    }

    override fun createCenterPanel(): JComponent = editorTextField

    override fun getPreferredFocusedComponent(): JComponent = editorTextField

    /**
     * Intercept Escape so that IdeaVIM gets first crack at it.
     *
     * When IdeaVIM is installed and the editor is in insert mode, we forward
     * Escape through IdeaVIM's own KeyHandler (switching to normal mode) and
     * suppress the default cancel/close behaviour.  When the editor is already
     * in normal mode — or IdeaVIM is not installed — we fall through to the
     * standard DialogWrapper cancel action and the dialog closes.
     */
    override fun createCancelAction(): ActionListener? {
        return ActionListener { e ->
            if (!tryHandleVimEscape()) {
                doCancelAction(e)
            }
        }
    }

    /**
     * If IdeaVIM is active and the current editor is in insert mode, dispatch
     * Escape through IdeaVIM's KeyHandler to switch to normal mode.
     *
     * IdeaVIM classes are accessed via reflection because they live in a
     * separate plugin classloader and are not on the compile classpath.
     * The optional <depends> on IdeaVIM in plugin.xml ensures the classes are
     * reachable at runtime when IdeaVIM is installed.
     *
     * @return true if IdeaVIM consumed the escape (dialog should stay open),
     *         false if the dialog should close as normal.
     */
    private fun tryHandleVimEscape(): Boolean {
        val editor = editorTextField.editor ?: return false
        val vimPlugin = PluginManagerCore.getPlugin(PluginId.getId("IdeaVIM"))
        if (vimPlugin == null || !vimPlugin.isEnabled) return false
        return try {
            val cl = vimPlugin.pluginClassLoader ?: return false

            // IjVimEditorKt.getVim(Editor) -> VimEditor
            val vimEditorInterface = cl.loadClass("com.maddyhome.idea.vim.api.VimEditor")
            val ijVimEditorKt = cl.loadClass("com.maddyhome.idea.vim.newapi.IjVimEditorKt")
            val ijEditorClass = Class.forName("com.intellij.openapi.editor.Editor")
            val getVim = ijVimEditorKt.getMethod("getVim", ijEditorClass)
            val vim = getVim.invoke(null, editor) ?: return false

            // Check vim mode via VimEditor.getMode() rather than VimEditor.getInsertMode().
            // insertMode delegates to EditorEx.isInsertMode (a cursor-shape flag) which is
            // set to true when entering INSERT mode but is never reset to false on exit —
            // it remains true even after the user presses Escape and enters normal mode.
            // getMode() returns the canonical Mode sealed class instance, which is always
            // accurate. We close the dialog when the mode is Mode.NORMAL; otherwise we
            // forward Escape to IdeaVIM's KeyHandler to switch modes.
            val getMode = vimEditorInterface.getMethod("getMode")
            val mode = getMode.invoke(vim) ?: return false
            val normalModeClass = cl.loadClass("com.maddyhome.idea.vim.state.mode.Mode\$NORMAL")
            if (normalModeClass.isInstance(mode)) return false

            // VimInjectorKt.getInjector() -> VimInjector
            val vimInjectorKt = cl.loadClass("com.maddyhome.idea.vim.api.VimInjectorKt")
            val getInjector = vimInjectorKt.getMethod("getInjector")
            val injector = getInjector.invoke(null) ?: return false

            // injector.executionContextManager.getEditorExecutionContext(vim) -> ExecutionContext
            val getEcm = injector.javaClass.getMethod("getExecutionContextManager")
            val ecm = getEcm.invoke(injector) ?: return false
            val getEditorCtx = ecm.javaClass.getMethod("getEditorExecutionContext", vimEditorInterface)
            val context = getEditorCtx.invoke(ecm, vim) ?: return false

            // KeyHandler.getInstance() -> KeyHandler
            val keyHandlerClass = cl.loadClass("com.maddyhome.idea.vim.KeyHandler")
            val getInstance = keyHandlerClass.getMethod("getInstance")
            val keyHandler = getInstance.invoke(null) ?: return false

            // keyHandler.keyHandlerState -> KeyHandlerState
            val getState = keyHandlerClass.getMethod("getKeyHandlerState")
            val keyHandlerState = getState.invoke(keyHandler) ?: return false

            // keyHandler.handleKey(vim, keystroke, context, keyHandlerState)
            val executionContextClass = cl.loadClass("com.maddyhome.idea.vim.api.ExecutionContext")
            val keyHandlerStateClass = cl.loadClass("com.maddyhome.idea.vim.state.KeyHandlerState")
            val handleKey = keyHandlerClass.getMethod(
                "handleKey",
                vimEditorInterface,
                KeyStroke::class.java,
                executionContextClass,
                keyHandlerStateClass,
            )
            handleKey.invoke(keyHandler, vim, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), context, keyHandlerState)
            true
        } catch (ex: Exception) {
            // IdeaVIM classes unavailable or API changed — let the dialog close normally
            false
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
