package com.hunterstich.ideavim.jetbridge

import com.hunterstich.jetbridge.provider.GeminiCLIProvider
import com.hunterstich.jetbridge.provider.OpenCodeProvider
import com.intellij.openapi.ui.Messages
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.extension.ExtensionHandler
import com.maddyhome.idea.vim.extension.VimExtension
import java.util.EnumSet
import javax.swing.text.JTextComponent

internal val provider = OpenCodeProvider()
// val provider = GeminiCLIProvider()

class JetbridgeVimExtension : VimExtension {

    override fun getName(): String = "jetbridge"

    override fun init() {
        injector.keyGroup.putKeyMapping(
            EnumSet.of(MappingMode.NORMAL, MappingMode.VISUAL),
            injector.parser.parseKeys("<leader>oa"),
            owner,
            askPromptHandler,
            false
        )
    }
}

private val askPromptHandler = object : ExtensionHandler {

    override fun execute(
        editor: VimEditor,
        context: ExecutionContext,
        operatorArguments: OperatorArguments
    ) {
        val userInput = captureDialogInput("@this ") ?: return
        var prompt = userInput.expandMacros(editor)

        provider.prompt(prompt)
        injector.messages.showStatusBarMessage(editor, prompt)
    }
}

private fun captureDialogInput(prepopulatedText: String): String? {
    // Create a custom dialog to control the text field behavior. The standard showInputDialog
    // uses the initialText as a hint that is overwritten when typing starts.
    val dialog = object : Messages.InputDialog(
        null,
        "Enter your prompt:",
        "${provider.displayName} prompt",
        Messages.getQuestionIcon(),
        "",
        null
    ) {

        override fun createTextFieldComponent(): JTextComponent? {
            val tf = super.createTextFieldComponent()
            javax.swing.SwingUtilities.invokeLater {
                tf.text = prepopulatedText
            }
            return tf
        }
    }

    dialog.show()
    return if (dialog.isOK) dialog.inputString else null
}

/**
 * Expand available macros
 *
 * `@this`: <@file path> <L:C-L:C>
 * TODO: Others
 */
private fun String.expandMacros(editor: VimEditor): String {
    var result = this
    if (result.contains("@this")) {
        var value = "@${editor.getPath()}"
        val caret = editor.primaryCaret()
        if (caret.hasSelection()) {
            // Start Position
            val startOffset = caret.selectionStart
            val startPos = editor.offsetToBufferPosition(startOffset)

            // End Position
            val endOffset = caret.selectionEnd
            val endPos = editor.offsetToBufferPosition(endOffset)

            // Format: Line:Column-Line:Column (1-based line for readability if desired)
            value += " L${startPos.line + 1}:C${startPos.column}-L${endPos.line + 1}:C${endPos.column}"
        } else {
            val line = caret.getBufferPosition().line
            value += " L${line}"
        }

        result = result.replace("@this", value)
    }

    // TODO: Replace other macros

    return result
}