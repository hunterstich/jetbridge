package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.core.AvailableProvider
import com.hunterstich.idea.jetbridge.core.ConfigStore
import com.hunterstich.idea.jetbridge.core.Dispatcher
import com.hunterstich.idea.jetbridge.core.OpenCodeApi
import com.hunterstich.idea.jetbridge.core.OpenCodeProvider
import com.hunterstich.idea.jetbridge.ui.OpenCodeConnectDialog
import com.hunterstich.idea.jetbridge.ui.PromptDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.ListSelectionModel

/**
 * Open a dialog for prompt input and send it to the current provider.
 */
class JetbridgePromptAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val contextSnapshot = editor.captureContextSnapshot(event)
        val rawInput = PromptDialog.show(
            event.project,
            "Jetbridge prompt:",
            ""
        ) ?: return
        Dispatcher.dispatch(rawInput, contextSnapshot)
    }
}

/**
 * Open a dialog for prompt input, pre-populated with "@this ", to the current provider
 */
class JetbridgeAskAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val contextSnapshot = editor.captureContextSnapshot(event)
        val rawInput = PromptDialog.show(
            event.project,
            "Jetbridge prompt:",
            "@this "
        ) ?: return
        Dispatcher.dispatch(rawInput, contextSnapshot)
    }
}

/**
 * Open a dialog with a dropdown for a server address and session name to connect to a specific
 * opencode instance.
 */
class JetbridgeConnectOpenCodeAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        // Force change to opencode provider
        if (ConfigStore.provider !is OpenCodeProvider) {
            ConfigStore.config.providerId = AvailableProvider.OpenCode.id
        }
        val provider = ConfigStore.provider as OpenCodeProvider
        launchOpenCodeConnectDialog(provider)
    }
}

internal fun launchOpenCodeConnectDialog(provider: OpenCodeProvider) {
    CoroutineScope(Dispatchers.IO).launch {
        val servers = OpenCodeApi.getServers()
        withContext(Dispatchers.Main) {
            val result = OpenCodeConnectDialog.show(
                servers = servers,
                initialAddress = ConfigStore.config.openCodeLastAddress,
                initialSessionId = ConfigStore.config.openCodeLastSessionId
            ) ?: return@withContext
            provider.connect(result.server, result.session)
        }
    }
}

/**
 * Open a list of providers to switch between.
 */
class JetbridgeSelectProviderAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val availableProviders = AvailableProvider.entries.map { it.displayName }
        val current = ConfigStore.provider.displayName
        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(availableProviders)
            .setTitle("Select provider")
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setSelectedValue(current, true)
            .setItemChosenCallback { selected ->
                val newProvider = AvailableProvider.fromDisplayName(selected);
                if (ConfigStore.provider.displayName != newProvider.displayName) {
                    ConfigStore.config.providerId = newProvider.id
                    ConfigStore.provider.reconnect(event.project?.basePath)
                }
            }
            .createPopup()

        popup.showInBestPositionFor(event.dataContext)
    }
}
