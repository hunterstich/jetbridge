package com.hunterstich.jetbridge.vim

import com.hunterstich.jetbridge.vim.provider.GeminiCLIProvider
import com.hunterstich.jetbridge.vim.provider.OpenCodeProvider
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

//val provider = OpenCodeProvider()
val provider = GeminiCLIProvider()

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
        // Get either the current line if nothing is selected, or the entire selected
        // block
        // Capture context
        var selectionContext = "path:${editor.getPath()}"
        val caret = editor.primaryCaret()
        if (caret.hasSelection()) {
            // Start Position
            val startOffset = caret.selectionStart
            val startPos = editor.offsetToBufferPosition(startOffset)

            // End Position
            val endOffset = caret.selectionEnd
            val endPos = editor.offsetToBufferPosition(endOffset)

            // Format: Line:Column-Line:Column (1-based line for readability if desired)
            selectionContext += " L${startPos.line + 1}:C${startPos.column}-L${endPos.line + 1}:C${endPos.column}"
        } else {
            val line = caret.getBufferPosition().line
            selectionContext += " L${line}"
        }

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
                    tf.text = "@this "
                }
                return tf;
            }
        }

        dialog.show()
        val userInput = if (dialog.isOK) dialog.inputString else null
        if (userInput == null) {
            // Do nothing without a prompt
            return
        }

        // TODO: Find and replace all macros: `@this`, `@buffer`, `@buffers`, `@visible`, etc.

        // macros:
        // @this <@src file path> <L:C-L:C>

        var prompt = "$selectionContext: $userInput"

        // TODO: Replace with a send off to the provider
        provider.prompt(prompt)
        injector.messages.showStatusBarMessage(editor, prompt)
    }
}
