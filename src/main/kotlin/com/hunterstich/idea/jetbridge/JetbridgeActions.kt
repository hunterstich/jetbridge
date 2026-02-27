package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.JetbridgeProviderManager.AvailableProvider
import com.hunterstich.idea.jetbridge.provider.OpenCodeProvider
import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeApi
import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeComponents
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.ListSelectionModel

class JetbridgePromptAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val rawInput = captureDialogInput(
            event.project,
            "${JetbridgeProviderManager.provider.displayName} prompt:",
            ""
        ) ?: return
        JetbridgeProviderManager.provider.prompt(rawInput, editor)
    }
}

class JetbridgeAskAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val rawInput = captureDialogInput(
            event.project,
            "${JetbridgeProviderManager.provider.displayName} prompt:",
            "@this "
        ) ?: return
        JetbridgeProviderManager.provider.prompt(rawInput, editor)
    }
}

class JetbridgeConnectOpenCodeAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        // Force change to opencode provider
        if (JetbridgeProviderManager.provider !is OpenCodeProvider) {
            JetbridgeSettings.instance.state.selectedProvider = AvailableProvider.OpenCode
        }
        val provider = JetbridgeProviderManager.provider as OpenCodeProvider

        CoroutineScope(Dispatchers.IO).launch {
            val servers = OpenCodeApi.getServers()
            withContext(Dispatchers.Main) {
                val result = OpenCodeComponents.showConnectDialog(servers) ?: return@withContext
                provider.connect(result.server, result.session)
            }
        }
    }
}

class JetbridgeSelectProviderAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val availableProviders = AvailableProvider.entries.map { it.displayName }
        val current = JetbridgeSettings.instance.state.selectedProvider.displayName
        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(availableProviders)
            .setTitle("Select provider")
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setSelectedValue(current, true)
            .setItemChosenCallback { selected ->
                val newProvider = AvailableProvider.fromDisplayName(selected);
                if (JetbridgeSettings.instance.state.selectedProvider != newProvider) {
                    JetbridgeSettings.instance.state.selectedProvider =
                        AvailableProvider.fromDisplayName(selected)
                    JetbridgeProviderManager.provider.reconnect(event.project)
                }
            }
            .createPopup()

        popup.showInBestPositionFor(event.dataContext)
    }
}

private fun captureDialogInput(project: Project?, title: String, prepopulatedText: String): String? {
    val dialog = PromptDialog(project, title, prepopulatedText, JetbridgeProviderManager.provider)
    dialog.show()
    return if (dialog.isOK) dialog.inputText else null
}