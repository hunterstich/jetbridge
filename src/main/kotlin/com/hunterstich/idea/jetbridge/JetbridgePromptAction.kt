package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
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

class JetbridgeConnectOpenCodeAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val result = captureConnectInput() ?: return
        println("Connect: address=${result.first}, session=${result.second}")
    }
}

private fun captureConnectInput(): Pair<String, String>? {
    val addressOptions = arrayOf("127.0.0.1:4096", "127.0.0.1:3000")
    val sessionOptions = arrayOf("jetbridge", "testbox")

    val addressCombo = ComboBox(DefaultComboBoxModel(addressOptions))
    val sessionCombo = ComboBox(DefaultComboBoxModel(sessionOptions)).apply {
        isEditable = true
    }

    val dialog = object : DialogWrapper(true) {
        init {
            title = "Connect to OpenCode"
            init()
        }

        override fun createCenterPanel(): JComponent {
            return panel {
                row("Address:") { cell(addressCombo) }
                row("Session:") { cell(sessionCombo) }
            }
        }
    }

    dialog.show()
    return if (dialog.isOK) {
        val address = addressCombo.selectedItem as? String ?: return null
        val session = (sessionCombo.selectedItem as? String)
            ?: (sessionCombo.editor.item as? String)
            ?: return null
        Pair(address, session)
    } else {
        null
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