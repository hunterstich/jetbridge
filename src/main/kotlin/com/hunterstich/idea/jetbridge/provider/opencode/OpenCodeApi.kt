package com.hunterstich.idea.jetbridge.provider.opencode

import com.intellij.util.io.awaitExit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object OpenCodeApi {

    private val client = HttpClient
        .newBuilder()
        .proxy(ProxySelector.getDefault())
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Send a prompt asynchronously to an OpenCode session.
     *
     * Makes an HTTP POST request to the OpenCode server at the specified address
     * with the prompt message. The prompt is wrapped in a [Message] object that
     * can optionally specify an agent to handle the prompt.
     *
     * @param address The server address in format "host:port" (e.g., "127.0.0.1:3000")
     * @param sessionId The unique identifier of the OpenCode session to send the prompt to
     * @param prompt The user's prompt text to send to OpenCode
     * @param agent Optional agent name to direct the prompt to a specific agent
     *              (e.g., "build", "plan", or a custom agent identifier)
     *
     * @return [Result<Boolean>] indicating success (true, status 200-299) or failure
     *         (false or exception wrapped in Result)
     */
    fun sendPromptAsync(
        address: String,
        sessionId: String,
        prompt: String,
        agent: String? = null,
    ): Result<Boolean> {
        val message = Message(
            agent = agent,
            parts = listOf(MessageParts(text = prompt, type = "text"))
        )
        val messageJson = Json.encodeToString(message)
        println("sending messageJson:$messageJson")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://$address/session/$sessionId/prompt_async"))
            .setHeader("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(messageJson))
            .build()
        return runCatching {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            println("got message response: $response, ${response.body()}")
            response.statusCode() in 200 ..299
        }
    }

    fun getSessions(address: String): Result<List<Session>> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://$address/session"))
            .GET()
            .build()
        return runCatching {
            val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
            json.decodeFromString(body)
        }
    }

    suspend fun getServers(): List<Server> {
        // Find the process that was started with `opencode --port`
        val pids = mutableListOf<String>()
        ProcessBuilder("pgrep", "-f", "opencode.*--port").start().let {
            it.inputStream
                .bufferedReader()
                .lines()
                .forEach { l -> pids.add(l) }
            it.awaitExit()
        }
        if (pids.isEmpty()) return emptyList()

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
            .filter { it.contains("TCP") }
            .mapNotNull { line ->
                """TCP (\d+\.\d+\.\d+\.\d+:\d+)""".toRegex().find(line)?.groupValues?.getOrNull(1)
            }
            .mapNotNull { address ->
                getServerPath(address).getOrNull()?.let { path -> Server(address, path) }
            }

        return servers
    }

    fun getServerPath(address: String): Result<ServerPath> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://$address/path"))
            .GET()
            .build()
        return runCatching {
            val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
            json.decodeFromString(body)
        }
    }

    /**
     * Connect to the opencode's server SSEs.
     */
    fun getEventsFlow(address: String): Flow<Event> = flow {
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
                        cancel()
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
                    cancel()
                    e.printStackTrace()
                    break
                }
            }
        }
    }


    /********** API Types **********/

    data class Server(
        val address: String,
        val path: ServerPath
    )

    @Serializable
    data class ServerPath(
        val home: String,
        val state: String,
        val config: String,
        val worktree: String,
        val directory: String
    )

    @Serializable
    data class Event(
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

    @Serializable
    data class Session(
        val id: String,
        val title: String,
        val directory: String,
        val time: Time
    )

    @Serializable
    data class Time(
        val created: Long,
        val updated: Long
    )

    @Serializable
    data class Message(
        val parts: List<MessageParts>,
        val agent: String? = null,
    )

    @Serializable
    data class MessageParts(
        val text: String,
        val type: String,
    )
}

