package com.hunterstich.idea.jetbridge.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun CoroutineScope.showNotification(
    project: Project,
    message: String,
    type: NotificationType = NotificationType.INFORMATION,
    duration: Long? = 3000L,
) {
    val notif = NotificationGroupManager.getInstance()
        .getNotificationGroup("Jetbridge notifications")
        .createNotification(message, type).apply {
            isRemoveWhenExpired = duration != null
        }
    notif.notify(project)
    if (duration != null) {
        launch {
            delay(duration)
            notif.expire()
        }
    }
}