package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.Bus
import com.hunterstich.idea.jetbridge.provider.ProviderEvent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.maddyhome.idea.vim.api.injector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val isDebug = true

class Jetbridge : ProjectActivity {

    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            // Listen for messages from long running provider tasks
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
                                scope.showNotification(project, msg.error, NotificationType.ERROR)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun CoroutineScope.showNotification(
    project: Project,
    message: String,
    type: NotificationType = NotificationType.INFORMATION,
    duration: Long = 3000L,
) {
    val notif = NotificationGroupManager.getInstance()
        .getNotificationGroup("Jetbridge notifications")
        .createNotification(message, type).apply {
            isRemoveWhenExpired = true
        }
    notif.notify(project)
    launch {
        delay(duration)
        notif.expire()
    }
}