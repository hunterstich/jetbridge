package com.hunterstich.idea.jetbridge.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

interface Provider {
    /** Human-readable provider name shown in UI surfaces such as actions and notifications. */
    val displayName: String

    /**
     * Send a user prompt to the provider.
     *
     * Implementations are responsible for preparing provider-specific context, handling connection
     * checks/retries, and dispatching errors through [Bus] as needed.
     */
    fun prompt(rawPrompt: String, snapshot: ContextSnapshot, target: Target)

    /**
     * Get a list of available connection targets (e.g., sessions, tmux windows) for this provider.
     */
    suspend fun getAvailableTargets(): List<Target>
}

/**
 * Represents a specific connection point within a provider, such as an OpenCode session
 * or a specific tmux session for gemini-cli.
 */
@Serializable
data class Target(
    val id: String,
    val label: String,
    val description: String,
    val provider: AvailableProvider,
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
    data class Message(val message: String) : ProviderEvent()
    data class Error(val error: String, val indefinite: Boolean = false) : ProviderEvent()
    data class Log(val category: String, val type: Type, val message: String) : ProviderEvent() {
        enum class Type {
            Info, Error, Warning
        }
    }
}

object Bus {

    private val _messages = MutableSharedFlow<ProviderEvent>(replay = 0)

    val messages: SharedFlow<ProviderEvent> = _messages.asSharedFlow()

    suspend fun emit(msg: ProviderEvent) {
        _messages.emit(msg)
    }

    suspend fun emitStatus(message: String) {
        _messages.emit(ProviderEvent.Status(message))
    }

    suspend fun emitError(message: String, indefinite: Boolean = false) {
        _messages.emit(ProviderEvent.Error(message, indefinite))
    }

    suspend fun emitLog(category: String, type: ProviderEvent.Log.Type, message: String) {
        _messages.emit(ProviderEvent.Log(category, type, message))
    }
}
