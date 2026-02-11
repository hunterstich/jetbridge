package com.hunterstich.jetbridge.vim.provider

import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

interface Provider {
    val displayName: String
    fun prompt(prompt: String)
}

class OpenCodeProvider : Provider {

    override val displayName: String = "opencode"

    private val client = HttpClient
        .newBuilder()
        .proxy(ProxySelector.getDefault())
        .build()

    override fun prompt(prompt: String) {

        val jsonBody = "{\"text\": \"$prompt\"}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:3000/tui/append-prompt"))
            .setHeader("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { it.body() }
            .thenAccept { println(it) }
    }
}
