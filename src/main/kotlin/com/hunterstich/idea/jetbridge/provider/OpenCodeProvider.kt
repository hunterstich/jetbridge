package com.hunterstich.idea.jetbridge.provider

import com.intellij.openapi.editor.Editor
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Serializable
private data class ServerPath(
    val home: String,
    val state: String,
    val config: String,
    val worktree: String,
    val directory: String
)

@Serializable
private data class Event(
    /**
     * Event type. Examples include:
     *
     * "server.connected"
     * "server.instance.disposed
     * "tui.prompt.append"
     * "tui.command.execute"
     * "message.updated"
     * "message.part.updated"
     * "question.asked"
     * "question.rejected"
     * "session.updated"
     * "session.status"
     * "session.diff"
     * "session.idle"
     * "server.heartbeat"
     */
    val type: String
)

/**
 * Implementation of Provider for OpenCode
 *
 * To interact with OpenCode, an opencode instance, started with `opencode --port`, must be
 * running somewhere in the path of the project.
 */
class OpenCodeProvider : Provider {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var eventJob: Job? = null

    private var isConnected = false
    private var address: String = ""
    private val baseUri: String
        get() = "http://$address"

    override val displayName: String = "opencode"

    private val client = HttpClient
        .newBuilder()
        .proxy(ProxySelector.getDefault())
        .build()

    private suspend fun ensureConnected(editor: Editor): Boolean {
        if (!isConnected) {
            val filePath = editor.virtualFile?.path
            this.isConnected = findOpenCodeServerPort(filePath) && address.isNotEmpty()
            if (!isConnected) {
                Bus.emit(
                    ProviderMessage.Error("No running opencode instance found in project path")
                )
                return false
            }
        }

        return true
    }

    override fun prompt(prompt: String, editor: Editor) {
        scope.launch {
            try {
                if (!ensureConnected(editor)) {
                    cancel()
                    return@launch
                }

                val appendResponse = client.send(
                    getAppendPromptRequest(prompt),
                    HttpResponse.BodyHandlers.ofString()
                )
                if (appendResponse.body() != "true") {
                    Bus.emit(ProviderMessage.Error(
                        "Unable to append prompt to opencode server at $baseUri"
                    ))
                    cancel()
                    return@launch
                }

                // TODO: If opencode is in the question.asked state, it blocks submission.
                // If unable to submit, add the prompt to the queue instead of discarding it!
                client.send(
                    getSubmitPromptRequest(),
                    HttpResponse.BodyHandlers.ofString()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Bus.emit(ProviderMessage.Error(
                    "Unable to submit prompt in opencode. Is it busy?"
                ))
                cancel()
            }
        }
    }

    private fun getAppendPromptRequest(prompt: String): HttpRequest {
        val jsonBody = "{\"text\": \"$prompt\"}"
        return HttpRequest.newBuilder()
            .uri(URI.create("$baseUri/tui/append-prompt"))
            .setHeader("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()
    }

    private fun getSubmitPromptRequest(): HttpRequest {
        return HttpRequest.newBuilder()
            .uri(URI.create("$baseUri/tui/submit-prompt"))
            .setHeader("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
    }

    private suspend fun getServerPath(uri: String): Result<ServerPath> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .GET()
            .build()
        val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
            ?: return Result.failure(Throwable("Response was null"))

        return body.runCatching { json.decodeFromString<ServerPath>(body) }
    }

    private suspend fun findOpenCodeServerPort(filePath: String?): Boolean {
        if (filePath == null) return false
        // Find the process that was started with `opencode --port`
        val pids = mutableListOf<String>()
        ProcessBuilder("pgrep", "-f", "opencode.*--port").start().let {
            it.inputStream
                .bufferedReader()
                .lines()
                .forEach { l -> pids.add(l) }
            it.awaitExit()
        }
        if (pids.isEmpty()) return false

        // Use the PID to look up the port opencode is running on
        val lsofOutputs = mutableListOf<String>()
        pids.forEach { pid ->
            ProcessBuilder("lsof", "-w", "-iTCP",
                "-sTCP:LISTEN", "-P", "-n", "-a", "-p", pid)
                .start().let {
                    it.inputStream
                        .bufferedReader()
                        .lines()
                        .forEach { l -> lsofOutputs.add(l) }

                    it.awaitExit()
                }
        }

        val servers = lsofOutputs
            .filter { it.contains("TCP") }.mapNotNull { line ->
                """TCP (\d+\.\d+\.\d+\.\d+:\d+)""".toRegex().find(line)?.groupValues?.getOrNull(1)
            }

        if (servers.isEmpty()) return false // no opencode instance running

        // Match the server that is located at the nearest ancestor of filePath
        val s = servers.map { s -> Pair(s, getServerPath("http://$s/path").getOrNull()) }
            .filter { it.second != null }
            .sortedByDescending { it.second?.directory!!.length }
            .firstOrNull { filePath.contains(it.second!!.directory) }

        // There is no opencode instance running in the file paths ancestry
        if (s == null) return false

        this.address = s.first

        eventJob?.cancel()
        eventJob = scope.launch {
            getEventsFlow(address).collect { handleOpenCodeEvent(it) }
        }

        return true
    }

    private suspend fun handleOpenCodeEvent(event: Event) {
        when (event.type) {
            "server.instance.disposed" -> {
                eventJob?.cancel()
                isConnected = false
            }
            "question.asked" -> {
                Bus.emit(ProviderMessage.Status("OpenCode asked a question"))
            }
        }
    }

    /**
     * Connect to the opencode's server SSEs.
     */
    private fun getEventsFlow(address: String): Flow<Event> = flow {
        coroutineScope {
            val url = "http://$address/event"
            val conn = (URL(url).openConnection() as HttpURLConnection).also {
                it.setRequestProperty("Accept", "text/event-stream")
                it.doInput = true
            }
            conn.connect()

            val inputReader = conn.getInputStream().bufferedReader()
            while (isActive) {
                try {
                    val line = inputReader.readLine()
                    if (line == null || conn.responseCode == -1) {
                        isConnected = false
                        break
                    }
                    if (line.isEmpty()) continue

                    if (line.startsWith("data: ")) {
                        val jsonStr = line.removePrefix("data: ")
                        try {
                            val event = json.decodeFromString<Event>(jsonStr)
                            emit(event)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    isConnected = false
                    e.printStackTrace()
                    break
                }
            }
        }
    }
}


