package com.hunterstich.idea.jetbridge.provider

import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    override fun prompt(rawPrompt: String, editor: Editor) {
        scope.launch {
            try {
                // TODO: Ensure there is a tmux session with the specific name
                // Append to gemini
                ProcessBuilder("tmux", "send-keys", "-t", "gemini", rawPrompt).start()
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