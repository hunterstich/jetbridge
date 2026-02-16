package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeApi
import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeComponents
import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
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
        provider.prompt(rawInput, editor)
    }
}

class JetbridgeConnectOpenCodeAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            val servers = OpenCodeApi.getServers()
            withContext(Dispatchers.Main) {
                val result = OpenCodeComponents.showConnectDialog(servers) ?: return@withContext
                provider.connect(result.server, result.session)
            }
        }
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