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
                if (!hasTmuxTarget(session)) {
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
                createProcess("tmux", "send-keys", "-t", session, prompt)
                    .start()
                    .waitFor()
                delay(100)
                // Submit the prompt
                createProcess("tmux", "send-keys", "-t", session, "C-m")
                    .start()
                    .waitFor()
            } catch (e: Exception) {
                Bus.emit(ProviderEvent.Error("Error sending prompt to tmux: ${e.message}"))
                Bus.emitLog(
                    GeminiCliProvider::class.java.name,
                    ProviderEvent.Log.Type.Error,
                    e.message ?: e.toString()
                )
            }
        }
    }

    override suspend fun getAvailableTargets(): List<Target> {
        val sessions = getSessionTargets()
        val panes = getPaneTargets()
        return sessions + panes
    }

    private fun getSessionTargets(): List<Target> {
        return try {
            val process = createProcess("tmux", "list-sessions", "-F", "#S").start()
            val sessions = process.inputStream.bufferedReader().readLines()
            sessions.filter { it.startsWith(tmuxPrefix) }.map { name ->
                Target(
                    id = name,
                    label = name,
                    description = "tmux session",
                    provider = AvailableProvider.GeminiCli,
                )
            }
        } catch (e: Exception) {
            scope.launch {
                Bus.emitLog(
                    GeminiCliProvider::class.java.name,
                    ProviderEvent.Log.Type.Error,
                    e.message ?: e.toString()
                )
            }
            emptyList()
        }
    }

    private fun getPaneTargets(): List<Target> {
        return try {
            val process = createProcess(
                "tmux", "list-panes", "-a",
                "-F", "#{session_name}:#{window_index}.#{pane_index}|#{session_name}|#{window_name}|#{window_index}|#{pane_index}"
            ).start()
            val lines = process.inputStream.bufferedReader().readLines()
            lines.mapNotNull { parsePaneLine(it) }
                .filter { it.windowName.contains("gem", ignoreCase = true) }
                .map { pane ->
                    Target(
                        id = pane.tmuxId,
                        label = pane.windowName,
                        description = "tmux pane · ${pane.tmuxId}",
                        provider = AvailableProvider.GeminiCli,
                    )
                }
        } catch (e: Exception) {
            scope.launch {
                Bus.emitLog(
                    GeminiCliProvider::class.java.name,
                    ProviderEvent.Log.Type.Error,
                    e.message ?: e.toString()
                )
            }
            emptyList()
        }
    }

    private fun hasTmuxTarget(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false

        return try {
            val process = createProcess("tmux", "has-session", "-t", name).start()
            val success = process.waitFor() == 0
            success
        } catch (e: Exception) {
            scope.launch {
                Bus.emitLog(
                    GeminiCliProvider::class.java.name,
                    ProviderEvent.Log.Type.Error,
                    e.message ?: e.toString()
                )
            }
            false
        }
    }
}

internal data class PaneInfo(
    val tmuxId: String,
    val sessionName: String,
    val windowName: String,
    val windowIndex: String,
    val paneIndex: String,
)

internal fun parsePaneLine(line: String): PaneInfo? {
    val parts = line.split("|")
    if (parts.size < 5) return null
    return PaneInfo(
        tmuxId = parts[0],
        sessionName = parts[1],
        windowName = parts[2],
        windowIndex = parts[3],
        paneIndex = parts[4],
    )
}
