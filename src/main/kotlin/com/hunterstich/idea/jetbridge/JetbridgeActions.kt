package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeApi
import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeComponents
import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val provider = OpenCodeProvider()

class JetbridgePromptAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val rawInput = captureDialogInput(
            event.project,
            "${provider.displayName} prompt:",
            ""
        ) ?: return
        provider.prompt(rawInput, editor)
    }
}

class JetbridgeAskAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val rawInput = captureDialogInput(
            event.project,
            "${provider.displayName} prompt:",
            "@this "
        ) ?: return
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

private fun captureDialogInput(project: Project?, title: String, prepopulatedText: String): String? {
    val dialog = PromptDialog(project, title, prepopulatedText)
    dialog.show()
    return if (dialog.isOK) dialog.inputText else null
}