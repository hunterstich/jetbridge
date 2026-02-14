package com.hunterstich.ideavim.jetbridge.provider

import com.hunterstich.jetbridge.provider.Bus
import com.hunterstich.jetbridge.provider.Provider
import com.hunterstich.jetbridge.provider.ProviderMessage
import com.intellij.openapi.editor.Editor
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Implementation of Provider for OpenCode
 *
 * To interact with OpenCode, an opencode instance, started with `opencode --port`, must be
 * running somewhere in the path of the project.
 */
class OpenCodeProvider : Provider {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var address: String = ""

    private val baseUri: String
        get() = "http://$address"

    override val displayName: String = "opencode"

    private val client = HttpClient
        .newBuilder()
        .proxy(ProxySelector.getDefault())
        .build()

    override fun prompt(prompt: String, editor: Editor) {
        scope.launch {
            try {
                val filePath = editor.virtualFile?.path
                if (!findOpenCodeServerPort(filePath) || address.isEmpty()) {
                    Bus.emit(ProviderMessage.Error(
                        "No opencode instance running in this project's path"
                    ))
                    cancel()
                    return@launch
                }

                println("using baseURI: $baseUri")
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

                client.send(
                    getSubmitPromptRequest(),
                    HttpResponse.BodyHandlers.ofString()
                )
            } catch (e: Exception) {
                Bus.emit(ProviderMessage.Error(
                    "Unable to prompt an opencode instance: ${e.message}"
                ))
                e.printStackTrace()
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

    @Serializable
    data class ServerPath(
        val home: String,
        val state: String,
        val config: String,
        val worktree: String,
        val directory: String
    )

    private val json = Json { ignoreUnknownKeys = true }

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
        if (filePath == null) return true // use any previously configured server hoping its there

        // Match the server that is located at the nearest ancestor of filePath
        val s = servers.map { s -> Pair(s, getServerPath("http://$s/path").getOrNull()) }
            .filter { it.second != null }
            .sortedByDescending { it.second?.directory!!.length }
            .firstOrNull { filePath.contains(it.second!!.directory) }

        // There is no opencode instance running in the file paths ancestry
        if (s == null) return false

        this.address = s.first
        return true
    }
}


