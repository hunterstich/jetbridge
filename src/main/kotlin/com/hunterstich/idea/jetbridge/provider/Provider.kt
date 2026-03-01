package com.hunterstich.idea.jetbridge.provider

import com.hunterstich.idea.jetbridge.utils.ContextSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface Provider {
    /** Human-readable provider name shown in UI surfaces such as actions and notifications. */
    val displayName: String

    /** Short connection summary used in status text (for example host, port, or session id). */
    val connectionDesc: String

    val isConnected: Boolean

    /**
     * Attempt to restore the provider's previous connection state.
     *
     * Implementations should use persisted provider-specific settings (for example, last used
     * address/session identifiers) and reconnect silently when possible.
     */
    fun reconnect(projectPath: String?)

    /**
     * Send a user prompt to the provider.
     *
     * Implementations are responsible for preparing provider-specific context, handling connection
     * checks/retries, and dispatching errors through [Bus] as needed.
     *
     * @param rawPrompt The raw prompt string captured from the UI before provider-specific parsing.
     * @param editor The active editor used for contextual macros (for example, current file or
     *   selection).
     * @param snapshot Editor context captured at action invocation time. This preserves caret and
     *   selection state if focus changes before prompt expansion.
     */
    fun prompt(rawPrompt: String, snapshot: ContextSnapshot)
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
