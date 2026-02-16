package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.OpenCodeProvider
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
        val rawInput = captureDialogInput("${provider.displayName} prompt:", "") ?: return
        provider.prompt(rawInput, editor)
    }
}

class JetbridgeAskAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val rawInput = captureDialogInput("${provider.displayName} prompt:", "@this ") ?: return
//        val userInput = captureTextAreaInput("${provider.displayName} prompt:", "@this ") ?: return
        provider.prompt(rawInput, editor)
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