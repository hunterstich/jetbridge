package com.hunterstich.ideavim.jetbridge.provider

import com.hunterstich.jetbridge.provider.Provider
import com.hunterstich.jetbridge.provider.ProviderMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch


/**
 * For gemini-cli, the provider must be started in a tmux session with:
 * `tmux new-session -s gemini 'gemini'`
 *
 * Commands can be programmatically sent to the tmux session with:
 * `tmux send-keys -t gemini "What is 2+2?"`
 * `tmux send-keys -t gemini C-m`
 */
class GeminiCliProvider : Provider {

    override val displayName: String = "gemini-cli"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _messages = MutableSharedFlow<ProviderMessage>(replay = 0)
    override val messages: SharedFlow<ProviderMessage> = _messages.asSharedFlow()

    override fun prompt(prompt: String) {
        scope.launch {
            try {
                // Append to gemini
                ProcessBuilder("tmux", "send-keys", "-t", "gemini", prompt).start()
                delay(100)
                // Submit the prompt
                ProcessBuilder("tmux", "send-keys", "-t", "gemini", "C-m").start()
            } catch (e: Exception) {
                println("Error sending prompt to tmux for gemini-cli: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}