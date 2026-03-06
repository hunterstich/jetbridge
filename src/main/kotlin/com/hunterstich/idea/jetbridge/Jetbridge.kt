package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.core.Bus
import com.hunterstich.idea.jetbridge.core.ConfigStore
import com.hunterstich.idea.jetbridge.core.ProviderEvent
import com.hunterstich.idea.jetbridge.core.ProviderEvent.Log.Type
import com.hunterstich.idea.jetbridge.ui.showNotification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log

private val isDebug = true
private val scope = CoroutineScope(Dispatchers.IO)

class Jetbridge : ProjectActivity {

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            // Initialize the configuration store
            val settingsInstance = ApplicationManager
                .getApplication()
                .getService(JetbridgeSettings::class.java)
            ConfigStore.initialize(settingsInstance.state)

            // Listen for messages from long running provider tasks
            attachBus(project)
        }
    }
}

/**
 * Listen for messages on the event bus and surface them as Intellij notifications.
 */
private fun attachBus(project: Project) {
    scope.launch {
        Bus.messages.collect { msg ->
            when (msg) {
                is ProviderEvent.Status -> {
                    if (isDebug) {
                        withContext(Dispatchers.Main) {
                            scope.showNotification(project, msg.message)
                        }
                    }
                }
                is ProviderEvent.Message -> {
                    withContext(Dispatchers.Main) {
                        scope.showNotification(project, msg.message)
                    }
                }
                is ProviderEvent.Error -> {
                    withContext(Dispatchers.Main) {
                        scope.showNotification(
                            project,
                            msg.error,
                            NotificationType.ERROR,
                            duration = if (msg.indefinite) null else 3000L
                        )
                    }
                }
                is ProviderEvent.Log -> {
                    val logger = Logger.getInstance(msg.category)
                    when (msg.type) {
                        Type.Info -> logger.info(msg.message)
                        Type.Error -> logger.error(msg.message)
                        Type.Warning -> logger.warn(msg.message)
                    }
                }
            }
        }
    }
}
