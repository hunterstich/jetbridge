package com.hunterstich.idea.jetbridge.core

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
     * @param snapshot Editor context captured at action invocation time. This preserves caret and
     *   selection state if focus changes before prompt expansion.
     * @param targetId Optional specific connection/session identifier to target. If null, use the
     *   last used or default connection.
     */
    fun prompt(rawPrompt: String, snapshot: ContextSnapshot, targetId: String? = null)

    /**
     * Get a list of available connection targets (e.g., sessions, tmux windows) for this provider.
     */
    suspend fun getAvailableTargets(): List<Target>

    /**
     * Resolve a target by its unique ID or its stable index (1, 2, 3...).
     */
    suspend fun getTarget(idOrIndex: String): Target?
}

/**
 * Represents a specific connection point within a provider, such as an OpenCode session
 * or a specific tmux session for gemini-cli.
 */
data class Target(
    val id: String,
    val label: String,
    val description: String,
    val provider: AvailableProvider,
    val index: Int? = null
)

enum class AvailableProvider(val id: Int, val displayName: String, val handle: String) {
    OpenCode(0, "opencode", "oc"),
    GeminiCli(1, "gemini-cli", "gem");

    companion object {
        fun fromDisplayName(str: String): AvailableProvider = when (str) {
            GeminiCli.displayName -> GeminiCli
            else -> OpenCode
        }

        fun fromHandle(handle: String): AvailableProvider? = when (handle) {
            OpenCode.handle -> OpenCode
            GeminiCli.handle -> GeminiCli
            else -> null
        }
    }
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
