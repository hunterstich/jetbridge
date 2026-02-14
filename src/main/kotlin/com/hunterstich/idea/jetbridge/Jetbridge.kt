package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.Bus
import com.hunterstich.idea.jetbridge.provider.ProviderMessage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class Jetbridge : ProjectActivity {

    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            // Listen for messages from long running provider tasks
            scope.launch {
                Bus.messages.collect { msg ->
                    when (msg) {
                        is ProviderMessage.Status -> println("status: ${msg.message}")
                        is ProviderMessage.Error -> {
                            withContext(Dispatchers.Main) { project.showError(msg.error) }
                        }
                    }
                }
            }
        }
    }
}

private fun Project.showError(message: String) {
    // TODO: Update to less intrusive UI component
    Messages.showErrorDialog(this, message, "Jetbridge")
}