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
     * Attempt to restore the provider's previous connection state.
     *
     * Implementations should use persisted provider-specific settings (for example, last used
     * address/session identifiers) and reconnect silently when possible.
     *
     * @param project The active IntelliJ project when available. Some providers may use this to
     *   scope reconnect behavior; others may ignore it.
     */
    fun reconnect(project: Project?)

    /**
     * Send a user prompt to the provider.
     *
     * Implementations are responsible for preparing provider-specific context, handling connection
     * checks/retries, and dispatching errors through [Bus] as needed.
     *
     * @param rawPrompt The raw prompt string captured from the UI before provider-specific parsing.
     * @param editor The active editor used for contextual macros (for example, current file or
     *   selection).
     */
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
