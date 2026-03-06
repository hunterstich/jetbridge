package com.hunterstich.idea.jetbridge.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json


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
    private val tmuxPrefix = "jetbridge"
    override val displayName: String = AvailableProvider.GeminiCli.displayName

    @Suppress("UsePlatformProcessAwaitExit")
    override fun prompt(rawPrompt: String, snapshot: ContextSnapshot, target: Target) {
        scope.launch {
            try {
                val session = target.id
                if (!hasTmuxSession(session)) {
                    // TODO: Create the session if it doesn't exist?
                    Bus.emit(ProviderEvent.Error("No gemini-cli tmux session found."))
                    cancel()
                    return@launch
                }

                val projectPath = snapshot.projectPath ?: ""
                var prompt = withContext(Dispatchers.Main) {
                    rawPrompt.expandInlineMacros(projectPath, snapshot)
                }
                prompt = prompt.cleanAllMacros()

                // TODO: Move to a dispatcher class?
                ConfigStore.config.lastTargetJson = Json.encodeToString(Target.serializer(), target)
                // Append to gemini
                ProcessBuilder("tmux", "send-keys", "-t", session, prompt)
                    .start()
                    .waitFor()
                delay(100)
                // Submit the prompt
                ProcessBuilder("tmux", "send-keys", "-t", session, "C-m")
                    .start()
                    .waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
                Bus.emit(ProviderEvent.Error("Error sending prompt to tmux: ${e.message}"))
            }
        }
    }

    override suspend fun getAvailableTargets(): List<Target> {
        return try {
            val process = ProcessBuilder("tmux", "list-sessions", "-F", "#S").start()
            val sessions = process.inputStream.bufferedReader().readLines()
            // Restrict sessions to those that start witih jetbridge*
            sessions.filter { it.startsWith(tmuxPrefix) }.mapIndexed { index, name ->
                Target(
                    id = name,
                    label = name,
                    description = "tmux session",
                    provider = AvailableProvider.GeminiCli,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun hasTmuxSession(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false

        return try {
            val process = ProcessBuilder("tmux", "has-session", "-t", name).start()
            val success = process.waitFor() == 0
            success
        } catch (e: Exception) {
            false
        }
    }
}
