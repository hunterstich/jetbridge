package com.hunterstich.idea.jetbridge.provider

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface Provider {
    val displayName: String
    val connectionDesc: String

    /**
     * Reconnect to the most recently used provider connection.
     */
    fun reconnect(project: Project?)
    fun prompt(rawPrompt: String, editor: Editor)
}

sealed class ProviderEvent {
    data class Status(val message: String) : ProviderEvent()
    data class Message(val message: String): ProviderEvent()
    data class Error(val error: String, val indefinite: Boolean = false) : ProviderEvent()
}

object Bus {

    private val _messages = MutableSharedFlow<ProviderEvent>(replay = 0)

    val messages: SharedFlow<ProviderEvent> = _messages.asSharedFlow()

    suspend fun emit(msg: ProviderEvent) {
        _messages.emit(msg)
    }
}
