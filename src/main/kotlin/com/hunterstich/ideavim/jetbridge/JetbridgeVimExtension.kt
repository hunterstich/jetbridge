package com.hunterstich.ideavim.jetbridge

import com.hunterstich.ideavim.jetbridge.provider.OpenCodeProvider
import com.hunterstich.jetbridge.provider.ProviderMessage
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.project.projectsDataDir
import com.intellij.openapi.ui.Messages
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.extension.ExtensionHandler
import com.maddyhome.idea.vim.extension.VimExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.EnumSet
import javax.swing.text.JTextComponent
import kotlin.io.path.name

// TODO: Add way to switch providers while running
private val provider = OpenCodeProvider()
// private val provider = GeminiCliProvider()

class JetbridgeVimExtension : VimExtension {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun getName(): String = "jetbridge"

    override fun init() {
        addKeyMapping("<leader>oo")
        addKeyMapping("<leader>oa", "@this ")
        addKeyMapping("<leader>ob", "@buffer ")

        scope.launch {
            provider.messages.collect { msg ->
                when (msg) {
                    is ProviderMessage.Status -> println("status: ${msg.message}")
                    is ProviderMessage.Error -> println("error: ${msg.error}")
                }
            }
        }
    }
}

private fun VimExtension.addKeyMapping(
    keys: String,
    promptPrefix: String = "",
    modes: EnumSet<MappingMode> = EnumSet.of(MappingMode.NORMAL, MappingMode.VISUAL),
    recursive: Boolean = false
) {
    injector.keyGroup.putKeyMapping(
        modes,
        injector.parser.parseKeys(keys),
        owner,
        promptHandler(promptPrefix),
        recursive
    )
}

fun promptHandler(initialInput: String = ""): ExtensionHandler {
    return object : ExtensionHandler {
        override fun execute(
            editor: VimEditor,
            context: ExecutionContext,
            operatorArguments: OperatorArguments
        ) {
            val userInput = captureDialogInput(initialInput) ?: return
            val prompt = userInput.expandMacros(editor)
            val filePath = editor.getPath()

            provider.prompt(prompt, filePath)
            injector.messages.showStatusBarMessage(editor, prompt)
        }
    }
}

private fun captureDialogInput(prepopulatedText: String): String? {
    // Create a custom dialog to control the text field behavior. The standard showInputDialog
    // uses the initialText as a hint that is overwritten when typing starts.
    val dialog = object : Messages.InputDialog(
        null,
        "",
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
 * `@buffer`: entire buffer contents
 */
private fun String.expandMacros(editor: VimEditor): String {
    var result = this
    if (result.contains("@this")) {
        var value = "@${editor.getPath()}"
        val caret = editor.primaryCaret()
        if (caret.hasSelection()) {
            val startOffset = caret.selectionStart
            val startPos = editor.offsetToBufferPosition(startOffset)

            val endOffset = caret.selectionEnd
            val endPos = editor.offsetToBufferPosition(endOffset)

            value += " L${startPos.line + 1}:C${startPos.column}-L${endPos.line + 1}:C${endPos.column}"
        } else {
            val line = caret.getBufferPosition().line
            value += " L${line}"
        }

        result = result.replace("@this", value)
    }

    // TODO: Expand @buffer
//    if (result.contains("@buffer")) {
//        editor.text()
//        val bufferText = editor.text().toString()
//        result = result.replace("@buffer", bufferText)
//    }

    return result
}