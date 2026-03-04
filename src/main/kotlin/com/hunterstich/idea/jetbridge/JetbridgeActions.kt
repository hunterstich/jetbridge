package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.core.ConfigStore
import com.hunterstich.idea.jetbridge.ui.PromptDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor

/**
 * Open a dialog for prompt input and send it to the current provider.
 */
class JetbridgePromptAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val contextSnapshot = editor.captureContextSnapshot(event)
        val result = PromptDialog.show(
            event.project,
            "Jetbridge prompt:",
            ""
        ) ?: return
        ConfigStore.getProvider(result.target.provider.id)
            .prompt(result.rawPrompt, contextSnapshot, result.target)
    }
}

/**
 * Open a dialog for prompt input, pre-populated with "@this ", to the current provider
 */
class JetbridgeAskAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor: Editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val contextSnapshot = editor.captureContextSnapshot(event)
        val result = PromptDialog.show(
            event.project,
            "Jetbridge prompt:",
            "@this "
        ) ?: return
        ConfigStore.getProvider(result.target.provider.id)
            .prompt(result.rawPrompt, contextSnapshot, result.target)
    }
}
