package com.hunterstich.idea.jetbridge.provider

import com.hunterstich.idea.jetbridge.cleanAllMacros
import com.hunterstich.idea.jetbridge.expandInlineMacros
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * For gemini-cli, the provider must be started in a tmux session with:
 * `tmux new-session -s gemini 'gemini'`
 *
 * Commands can be programmatically sent to the tmux session with:
 * `tmux send-keys -t gemini "What is 2+2?"`
 * `tmux send-keys -t gemini C-m`
 */
class GeminiCliProvider : Provider {

    private val scope = CoroutineScope(Dispatchers.IO)

    // TODO: Move into JetbridgeSetting and allow customization of tmux session name
    private val tmuxSessionName = "gemini-jetbridge"

    override val displayName: String = "gemini-cli"
    override val connectionDesc: String
        get() = tmuxSessionName

    override fun reconnect(project: Project?) {
        if (hasTmuxSession(tmuxSessionName)) {
            scope.launch {
                Bus.emit(
                    ProviderEvent.Status(
                        "Jetbridge: Connected to gemini-cli tmux session \"$tmuxSessionName\""
                    )
                )
            }
        } else {
            scope.launch {
                Bus.emit(
                    ProviderEvent.Error(
                        "Jetbridge: No gemini-cli tmux session found for \"$tmuxSessionName\""
                    )
                )
            }
        }
    }

    override fun prompt(rawPrompt: String, editor: Editor) {
        scope.launch {
            try {
                if (!hasTmuxSession(tmuxSessionName)) {
                    Bus.emit(ProviderEvent.Error(
                        "No tmux session '$tmuxSessionName' found. Start one with: " +
                                "tmux new-session -s $tmuxSessionName 'gemini'",
                        indefinite = true
                    ))
                    return@launch
                }

                val projectPath = editor.project?.basePath ?: ""
                var prompt = withContext(Dispatchers.Main) {
                    rawPrompt.expandInlineMacros(editor, projectPath)
                }
                prompt = prompt.cleanAllMacros()

                // Append to gemini
                ProcessBuilder("tmux", "send-keys", "-t", tmuxSessionName, prompt)
                    .start()
                    .awaitExit()
                delay(100)
                // Submit the prompt
                ProcessBuilder("tmux", "send-keys", "-t", tmuxSessionName, "C-m")
                    .start()
                    .awaitExit()
            } catch (e: Exception) {
                e.printStackTrace()
                Bus.emit(ProviderEvent.Error("Error sending prompt to tmux: ${e.message}"))
            }
        }
    }

    private fun hasTmuxSession(name: String): Boolean {
        return try {
            val process = ProcessBuilder("tmux", "has-session", "-t", name).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}