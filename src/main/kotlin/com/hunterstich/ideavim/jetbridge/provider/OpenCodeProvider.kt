package com.hunterstich.ideavim.jetbridge.provider

import com.hunterstich.jetbridge.provider.Provider
import com.hunterstich.jetbridge.provider.ProviderMessage
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class OpenCodeProvider : Provider {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var ip: String = "127.0.0.1"
    private var port: Int = 3000

    private val baseUri: String
        get() = "http://$ip:$port"

    override val displayName: String = "opencode"

    private val _messages = MutableSharedFlow<ProviderMessage>(replay = 0)
    override val messages: SharedFlow<ProviderMessage> = _messages.asSharedFlow()

    private val client = HttpClient
        .newBuilder()
        .proxy(ProxySelector.getDefault())
        .build()

    override fun prompt(prompt: String) {
        scope.launch {
            if (!findOpenCodeServerPort()) return@launch

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

    private suspend fun findOpenCodeServerPort(): Boolean {
        // Find the process that was started with `opencode --port`
        val pGrepOutput = mutableListOf<String>()
        ProcessBuilder("pgrep", "-f", "opencode.*--port").start().let {
            it.inputStream
                .bufferedReader()
                .lines()
                .forEach { l -> pGrepOutput.add(l) }
            it.awaitExit()
        }
        val process: String = pGrepOutput.firstOrNull { it.toIntOrNull() != null } ?: return false

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
        val result = lsofOutput
            .filter { it.contains("TCP") }
            .map { line ->
                """TCP (\d+\.\d+\.\d+\.\d+):(\d+)""".toRegex().find(line)?.groupValues
            }
            .firstOrNull()

        // IP address is the first group if we need that
        val ip = result?.getOrNull(1) ?: return false
        val port = result.getOrNull(2)?.toIntOrNull() ?: return false

        this.ip = ip
        this.port = port
        return true
    }
}


