package com.hunterstich.jetbridge.vim.provider

import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

interface Provider {
    val displayName: String
    fun prompt(prompt: String)
}

class OpenCodeProvider : Provider {

    // TODO: Find instead of hardcode
    private val port: String
        get() = "3000"

    private val baseUri: String
        get() = "http://localhost:$port/"

    override val displayName: String = "opencode"

    private val client = HttpClient
        .newBuilder()
        .proxy(ProxySelector.getDefault())
        .build()

    override fun prompt(prompt: String) {
        client.sendAsync(getAppendPromptRequest(prompt), HttpResponse.BodyHandlers.ofString())
            .thenCompose { response ->
                // TODO: Change to check status instead?
                if (response.body() == "true") {
                    client.sendAsync(getSubmitPromptRequest(), HttpResponse.BodyHandlers.ofString())
                } else {
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

    override fun prompt(prompt: String) {
        try {
            // Append to gemini
            ProcessBuilder("tmux", "send-keys", "-t", "gemini", prompt).start()
            // Submit the prompt
            ProcessBuilder("tmux", "send-keys", "-t", "gemini", "C-m").start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
