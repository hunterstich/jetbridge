package com.hunterstich.jetbridge.provider

import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

interface Provider {
    val displayName: String
    // TODO: Add return type with status/error
    fun prompt(prompt: String)
}

class OpenCodeProvider : Provider {

    private val scope = CoroutineScope(Dispatchers.IO)

    // TODO: Find instead of hardcode
    private val port: String
        get() = "3000"

    private val baseUri: String
        get() = "http://localhost:$port"

    override val displayName: String = "opencode"

    private val client = HttpClient
        .newBuilder()
        .proxy(ProxySelector.getDefault())
        .build()

    override fun prompt(prompt: String) {
//        findOpencodeServerPort()
        client.sendAsync(getAppendPromptRequest(prompt), HttpResponse.BodyHandlers.ofString())
            .thenCompose { response ->
                // TODO: Change to check status instead?
                if (response.body() == "true") {
                    client.sendAsync(getSubmitPromptRequest(), HttpResponse.BodyHandlers.ofString())
                } else {
                    println("Unable to append prompt to opencode")
                    CompletableFuture.completedFuture(null)
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

    private fun findOpencodeServerPort() {
        scope.launch {

            // Find the process that was started with `opencode --port`
            val pGrepOutput = mutableListOf<String>()
            ProcessBuilder("pgrep", "-f", "opencode.*--port").start().let {
                it.inputStream
                    .bufferedReader()
                    .lines()
                    .forEach { l -> pGrepOutput.add(l) }
                it.awaitExit()
            }
            val process: String? = pGrepOutput.firstOrNull { it.toIntOrNull() != null }

            // Use the PID to look up the port opencode is running on
            if (process != null) {
                val lsofOutput = mutableListOf<String>()
                ProcessBuilder("lsof", "-w", "-iTCP",
                    "-sTCP:LISTEN", "-P", "-n", "-a", "-p", process)
                    .start().let {
                        it.inputStream
                            .bufferedReader()
                            .lines()
                            .forEach { l -> lsofOutput.add(l) }
                        it.awaitExit()
                    }
                println("lsofOutput: $lsofOutput")
                // TODO: For valid output and parse out port number
            } else {
                // TODO: Show error message that a running opencode could not be found
            }
        }
    }
}


/**
 * For gemini-cli, the provider must be started in a tmux session with:
 * `tmux new-session -s gemini 'gemini'`
 *
 * Commands can be programmatically sent to the tmux session with:
 * `tmux send-keys -t gemini "What is 2+2?"`
 * `tmux send-keys -t gemini C-m`
 */
class GeminiCLIProvider : Provider {

    override val displayName: String = "gemini-cli"
    private val scope = CoroutineScope(Dispatchers.IO)

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
