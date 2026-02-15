package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.Bus
import com.hunterstich.idea.jetbridge.provider.ProviderMessage
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                        is ProviderMessage.Status -> {
                            println("status: ${msg.message}")
                            withContext(Dispatchers.Main) {
                                scope.showNotification(project, msg.message)
                            }
                        }
                        is ProviderMessage.Error -> {
                            withContext(Dispatchers.Main) {
                                println("showing error message")
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