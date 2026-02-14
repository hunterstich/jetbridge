package com.hunterstich.jetbridge.provider

import com.maddyhome.idea.vim.api.VimEditor
import kotlinx.coroutines.flow.SharedFlow

interface Provider {
    val displayName: String
    val messages: SharedFlow<ProviderMessage>
    fun prompt(prompt: String, filePath: String?)
}

sealed class ProviderMessage {
    data class Status(val message: String) : ProviderMessage()
    data class Error(val error: String) : ProviderMessage()
}
