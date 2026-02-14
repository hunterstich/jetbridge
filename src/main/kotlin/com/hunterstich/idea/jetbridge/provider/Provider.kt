package com.hunterstich.idea.jetbridge.provider

import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

interface Provider {
    val displayName: String
    fun prompt(prompt: String, event: Editor)
}

sealed class ProviderMessage {
    data class Status(val message: String) : ProviderMessage()
    data class Error(val error: String) : ProviderMessage()
}

object Bus {

    private val _messages = MutableSharedFlow<ProviderMessage>(replay = 0)

    val messages: SharedFlow<ProviderMessage> = _messages.asSharedFlow()

    suspend fun emit(msg: ProviderMessage) {
        _messages.emit(msg)
    }
}

