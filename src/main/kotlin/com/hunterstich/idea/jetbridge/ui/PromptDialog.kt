package com.hunterstich.idea.jetbridge.ui

import com.hunterstich.idea.jetbridge.core.AvailableProvider
import com.hunterstich.idea.jetbridge.core.ConfigStore
import com.hunterstich.idea.jetbridge.core.Target
import com.hunterstich.idea.jetbridge.core.allMacroRegex
import com.hunterstich.idea.jetbridge.core.allMacros
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Document
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
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.maddyhome.idea.vim.KeyHandler
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.helper.inNormalMode
import com.maddyhome.idea.vim.newapi.ij
import com.maddyhome.idea.vim.newapi.vim
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke


/**
 * A multi-line prompt dialog with macro highlighting and autocomplete.
 *
 * - Enter submits the dialog (OK).
 * - Shift+Enter inserts a newline.
 * - Escape closes the dialog.
 * - Macros like @this, @file, etc. are highlighted as the user types.
 * - Autocomplete for @oc:1, @gem, etc.
 * - IdeaVIM keybindings work if the user has `set ideavimsupport+=dialog` in their .ideavimrc.
 */
class PromptDialog(
    private val project: Project?,
    dialogTitle: String,
    prepopulatedText: String,
) : DialogWrapper(project) {

    private val centerPanel: JComponent
    private val editorTextField: EditorTextField
    private val hintLabel: JBLabel = JBLabel("").apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.emptyTop(6)
    }

    val vimPlugin = PluginManagerCore.getPlugin(PluginId.getId("IdeaVIM"))
    val vimEnabled = vimPlugin != null && vimPlugin.isEnabled()

    private val macroCompletionProvider = MacroCompletionProvider()
    val inputText: String
        get() = editorTextField.text

    val lastUsedTarget: Target?
    var target: Target? = null
    val hasTarget: Boolean
        get() = target != null

    data class Result(val rawPrompt: String, val target: Target)

    companion object {
        fun show(project: Project?, title: String, prepopulatedText: String): Result? {
            val dialog = PromptDialog(project, title, prepopulatedText)
            dialog.show()
            return if (dialog.isOK && dialog.hasTarget) Result(
                rawPrompt = dialog.inputText,
                target = dialog.target!!
            ) else null
        }
    }

    init {
        title = dialogTitle

        lastUsedTarget = if (ConfigStore.config.lastTargetJson != null) {
            Json.decodeFromString(Target.serializer(), ConfigStore.config.lastTargetJson!!)
        } else {
            null
        }

        editorTextField = TextFieldWithAutoCompletion(
            project,
            macroCompletionProvider,
            true, // showCompletionPopup = true
            prepopulatedText
        ).apply {
            setOneLineMode(false)
            // Re-use our document if possible, though TextFieldWithAutoCompletion creates its own.
            // For now, let's just let it create its own and we'll apply settings.
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
                        val editor = editorTextField.editor
                        val lookup =
                            if (editor != null) LookupManager.getActiveLookup(editor) else null
                        if (lookup != null) {
                            // Trigger the standard "choose item" action
                            ActionManager.getInstance()
                                .getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)
                                ?.actionPerformed(e)
                            return
                        }

                        if (!hasTarget) {
                            // TODO: Show an error that a target must be specified
                            return
                        }
                        doOKAction()
                    }
                }
                enterAction.registerCustomShortcutSet(
                    CustomShortcutSet.fromString("ENTER"),
                    editor.contentComponent,
                    disposable,
                )
                parseForMacros(editor)
            }
        }

        editorTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val editor = editorTextField.editor ?: return
                parseForMacros(editor)
                parseForTarget()

                // Make sure the auto-complete popup shows as soon as @ or : are typed
                val newFragment = event.newFragment.toString()
                if (newFragment.contains(":") || newFragment.contains("@")) {
                    project?.let { AutoPopupController.getInstance(it).scheduleAutoPopup(editor) }
                }
            }
        }, this.disposable)

        centerPanel = JPanel(BorderLayout()).apply {
            add(editorTextField, BorderLayout.CENTER)
            add(hintLabel, BorderLayout.SOUTH)
        }

        registerVimCommandShortcuts()

        init()
        updateTarget(lastUsedTarget)
        parseForTarget()
    }

    override fun createCenterPanel(): JComponent = centerPanel

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
                    if (!hasTarget) {
                        commandLine.close(refocusOwningEditor = false, resetCaret = false)
                        doCancelAction()
                    } else {
                        commandLine.close(refocusOwningEditor = false, resetCaret = false)
                        doOKAction()
                    }

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

    private fun updateTarget(target: Target?) {
        val newTarget = target ?: lastUsedTarget
        this.target = newTarget

        isOKActionEnabled = newTarget != null
        if (newTarget != null) {
            hintLabel.text =
                "Target: ${newTarget.provider.displayName} [${newTarget.label}]"
        } else {
            hintLabel.text = "Specify a target with @oc, @gem, etc."
        }
    }

    private fun parseForTarget() {
        val text = editorTextField.text
        val routingMatch = """@(oc|gem)(:([a-zA-Z0-9.\-/:_]+))?""".toRegex().find(text)

        if (routingMatch != null) {
            val handle = routingMatch.groupValues[1]
            val indexStr = routingMatch.groupValues[3]
            val index = indexStr.takeIf { it.isNotEmpty() }?.toIntOrNull()

            if (index != null) {
                // First try to get it from the cached list in the completion provider.
                // If it's empty (e.g., on dialog open), resolve it manually.
                val target = macroCompletionProvider.cachedTargets.getOrNull(index)
                updateTarget(target)
            } else {
                updateTarget(null)
            }
        } else {
            updateTarget(null)
        }
    }

    /**
     * Scans the editor text for known macros and applies highlight markers.
     */
    private fun parseForMacros(editor: Editor) {
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

private class MacroCompletionProvider :
    TextFieldWithAutoCompletionListProvider<String>(emptyList()) {

    var cachedTargets: List<Target> = emptyList()

    override fun getLookupString(item: String): String = item

    override fun getPrefix(text: String, offset: Int): String {
        var i = offset - 1
        while (i >= 0 && !text[i].isWhitespace()) {
            i--
        }
        return text.substring(i + 1, offset)
    }

    override fun getItems(
        prefix: String?,
        cached: Boolean,
        parameters: CompletionParameters
    ): Collection<String> {
        val currentPrefix = prefix ?: ""
        if (!currentPrefix.startsWith("@")) return emptyList()

        val macroPart = currentPrefix.substring(1)
        if (macroPart.contains(":")) {
            val handle = macroPart.substringBefore(":").lowercase()
            val provider =
                AvailableProvider.fromHandle(handle)?.let { ConfigStore.getProvider(it.id) }
            if (provider != null) {
                return runBlocking {
                    cachedTargets = provider.getAvailableTargets()
                    List(cachedTargets.size) { index -> "@$handle:$index" }
                }
            }
        }

        val macros = allMacros.toMutableList()
        AvailableProvider.entries.forEach { macros.add("@${it.handle}:") }
        return macros.filter { it.startsWith(currentPrefix) }
    }

    override fun createLookupBuilder(item: String): LookupElementBuilder {
        var builder = super.createLookupBuilder(item)
        if (item.contains(":")) {
            val handle = item.substring(1, item.indexOf(":")).lowercase()
            val indexPart = item.substring(item.indexOf(":") + 1)
            val index = indexPart.toIntOrNull()

            if (index != null && index < cachedTargets.size) {
                val providerEnum = AvailableProvider.fromHandle(handle)
                val provider = providerEnum?.let { ConfigStore.getProvider(it.id) }
                if (provider != null && cachedTargets.lastIndex >= index) {
                    val target = cachedTargets[index]
                    builder = builder.withPresentableText("${index}. ${target.label}")
                        .withTailText("  ${target.description}", true)
                }
            }
        }
        return builder
    }
}

