package com.hunterstich.idea.jetbridge.core

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

    override val displayName: String = AvailableProvider.GeminiCli.displayName
    override val connectionDesc: String
        get() = tmuxSessionName

    override val isConnected: Boolean
        get() = hasTmuxSession(tmuxSessionName)

    override fun reconnect(projectPath: String?) {
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

    @Suppress("UsePlatformProcessAwaitExit")
    override fun prompt(rawPrompt: String, snapshot: ContextSnapshot, targetId: String?) {
        scope.launch {
            try {
                val targetSession = targetId ?: tmuxSessionName
                if (!hasTmuxSession(targetSession)) {
                    // Create the session if it doesn't exist
                    // TODO: Should session creation also launch a shell?
                    try {
                        ProcessBuilder("tmux", "new-session", "-d", "-s", targetSession, "gemini")
                            .start()
                            .waitFor()
                    } catch (e: Exception) {
                        Bus.emit(ProviderEvent.Error("Unable to create tmux session: ${e.message}"))
                        return@launch
                    }
                }

                val projectPath = snapshot.projectPath ?: ""
                var prompt = withContext(Dispatchers.Main) {
                    rawPrompt.expandInlineMacros(projectPath, snapshot)
                }
                prompt = prompt.cleanAllMacros()

                // Append to gemini
                ProcessBuilder("tmux", "send-keys", "-t", targetSession, prompt)
                    .start()
                    .waitFor()
                delay(100)
                // Submit the prompt
                ProcessBuilder("tmux", "send-keys", "-t", targetSession, "C-m")
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
            sessions.mapIndexed { index, name ->
                Target(
                    id = name,
                    label = name,
                    description = "tmux session",
                    provider = AvailableProvider.GeminiCli,
                    index = index + 1
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTarget(idOrIndex: String): Target? {
        val targets = getAvailableTargets()
        return targets.find { it.id == idOrIndex || it.index.toString() == idOrIndex }
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
