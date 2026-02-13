package com.hunterstich.jetbridge.provider

import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

interface Provider {
    val displayName: String
    val messages: SharedFlow<ProviderMessage>
    fun prompt(prompt: String)
}

sealed class ProviderMessage {
    data class Status(val message: String) : ProviderMessage()
    data class Error(val error: String) : ProviderMessage()
}

class OpenCodeProvider : Provider {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var port: Int = 3000

    private val baseUri: String
        get() = "http://localhost:$port"

    override val displayName: String = "opencode"

    private val _messages = MutableSharedFlow<ProviderMessage>(replay = 0)
    override val messages: SharedFlow<ProviderMessage> = _messages.asSharedFlow()

    private val client = HttpClient
        .newBuilder()
        .proxy(ProxySelector.getDefault())
        .build()

    override fun prompt(prompt: String) {
        scope.launch {
            val port = findOpenCodeServerPort()
            if (port == null) {
                _messages.emit(ProviderMessage.Error("No process found that was started " +
                        "with `opencode --port`. Is opencode running?"))
                return@launch
            }
            this@OpenCodeProvider.port = port

            val appendResponse = client.send(
                getAppendPromptRequest(prompt),
                HttpResponse.BodyHandlers.ofString()
            )
            if (appendResponse.body() != "true") {
                _messages.emit(
                    ProviderMessage.Error("Unable to append prompt to opencode server at $baseUri")
                )
                return@launch
            }

            client.send(
                getSubmitPromptRequest(),
                HttpResponse.BodyHandlers.ofString()
            )
            // TODO: Maybe handle errors. Or just send off without care
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

    private suspend fun findOpenCodeServerPort(): Int? {
        // Find the process that was started with `opencode --port`
        val pGrepOutput = mutableListOf<String>()
        ProcessBuilder("pgrep", "-f", "opencode.*--port").start().let {
            it.inputStream
                .bufferedReader()
                .lines()
                .forEach { l -> pGrepOutput.add(l) }
            it.awaitExit()
        }
        val process: String = pGrepOutput.firstOrNull { it.toIntOrNull() != null } ?: return null

        // Use the PID to look up the port opencode is running on
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

        // Parse the `lsofOutput` variable and extract the port number at the end of
        // the ip address into a variable called `port`. Here is what the lsofOutput string
        // looks like:
        // [COMMAND    PID   USER   FD   TYPE             DEVICE SIZE/OFF NODE NAME, .opencode 8620 laptop   20u  IPv4 0xf341b5c7aeeffce5      0t0  TCP 127.0.0.1:3000 (LISTEN)]
        val port = lsofOutput
            .filter { it.contains("TCP") }
            .map { line ->
                Regex("""TCP \d+\.\d+\.\d+\.\d+:(\d+)""").find(line)?.groupValues?.get(1)
            }
            .firstOrNull()
            ?.toIntOrNull()

        return port
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
