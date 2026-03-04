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

    private val tmuxPrefix = "jetbridge"

    override val displayName: String = AvailableProvider.GeminiCli.displayName

    private var session: String? = ConfigStore.config.geminiCliLastSessionName
    override val connectionDesc: String
        get() = session ?: "none"

    override val isConnected: Boolean
        get() = hasTmuxSession(session)

    override fun reconnect(projectPath: String?) {
        if (hasTmuxSession(session)) {
            scope.launch {
                Bus.emit(
                    ProviderEvent.Status(
                        "Jetbridge: Connected to gemini-cli tmux session \"$session\""
                    )
                )
            }
        } else {
            scope.launch {
                Bus.emit(
                    ProviderEvent.Error(
                        "Jetbridge: No gemini-cli tmux session found"
                    )
                )
            }
        }
    }

    @Suppress("UsePlatformProcessAwaitExit")
    override fun prompt(rawPrompt: String, snapshot: ContextSnapshot, targetId: String?) {
        scope.launch {
            try {
                val targetSession = targetId ?: session
                if (targetSession == null || !hasTmuxSession(targetSession)) {
                    // Create the session if it doesn't exist
                    // TODO: Should session creation also launch a shell?
//                    try {
//                        ProcessBuilder("tmux", "new-session", "-d", "-s", targetSession, "gemini")
//                            .start()
//                            .waitFor()
//                    } catch (e: Exception) {
//                        Bus.emit(ProviderEvent.Error("Unable to create tmux session: ${e.message}"))
//                        return@launch
//                    }
                    Bus.emit(ProviderEvent.Error("No gemini-cli tmux session found."))
                    return@launch
                }

                ConfigStore.config.geminiCliLastSessionName = targetSession
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
            sessions.filter { it.startsWith(tmuxPrefix) }.mapIndexed { index, name ->
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
