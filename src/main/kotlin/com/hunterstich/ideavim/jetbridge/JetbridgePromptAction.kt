package com.hunterstich.ideavim.jetbridge

import com.hunterstich.ideavim.jetbridge.provider.OpenCodeProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import javax.swing.text.JTextComponent

private val provider = OpenCodeProvider()

class JetbridgePromptAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return

        val userInput = captureDialogInput("${provider.displayName} prompt:", "@this ") ?: return
        provider.prompt(userInput.expandMacros(editor), editor)
    }
}

private fun captureDialogInput(title: String, prepopulatedText: String): String? {
    // Create a custom dialog to control the text field behavior. The standard showInputDialog
    // uses the initialText as a hint that is overwritten when typing starts.
    val dialog = object : Messages.InputDialog(
        null,
        "",
        title,
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

private fun String.expandMacros(editor: Editor): String {
    var result = this
    if (result.contains("@this")) {
        var value = "@${editor.virtualFile.path}"
        val caret = editor.caretModel.primaryCaret
        if (caret.hasSelection()) {
            val startPos = caret.selectionStartPosition
            val endPos = caret.selectionEndPosition
            value += " L${startPos.line + 1}:C${startPos.column}-L${endPos.line + 1}:C${endPos.column}"
        } else {
            value += " L${caret.selectionStartPosition.line}"
        }
        result = result.replace("@this", value)
    }

    // TODO: Expand @buffer
    // if (result.contains("@buffer")) {
    //     editor.text()
    //     val bufferText = editor.text().toString()
    //     result = result.replace("@buffer", bufferText)
    // }

    return result
}