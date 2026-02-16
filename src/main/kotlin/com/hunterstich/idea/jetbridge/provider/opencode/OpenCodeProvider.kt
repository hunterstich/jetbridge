package com.hunterstich.idea.jetbridge.provider.opencode

import com.hunterstich.idea.jetbridge.cleanAllMacros
import com.hunterstich.idea.jetbridge.expandInlineMacros
import com.hunterstich.idea.jetbridge.provider.Bus
import com.hunterstich.idea.jetbridge.provider.Provider
import com.hunterstich.idea.jetbridge.provider.ProviderEvent
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
import kotlinx.coroutines.withContext
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

/**
 * Implementation of Provider for OpenCode
 *
 * To interact with OpenCode, an opencode instance, started with `opencode --port`, must be
 * running somewhere in the path of the project.
 */
class OpenCodeProvider : Provider {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var isConnected = false
    private var server: OpenCodeApi.Server? = null
    private var session: OpenCodeApi.Session? = null
    private var sseJob: Job? = null

    override val displayName: String = "opencode"

    override fun prompt(rawPrompt: String, editor: Editor) {
        scope.launch {
            try {
                if (!ensureConnected(editor)) {
                    Bus.emit(
                        ProviderEvent.Error("No running opencode instance found in project path")
                    )
                    cancel()
                    return@launch
                }

                // TODO: Clean up the messy null handling
                val address = server?.address
                val serverPath = server?.path?.directory
                val sessionId = session?.id
                if (address == null || serverPath == null || sessionId == null) {
                    cancel()
                    return@launch
                }

                var prompt = withContext(Dispatchers.Main) {
                    rawPrompt.expandInlineMacros(editor, serverPath)
                }
                val agent = extractAgent(prompt)
                prompt = prompt.cleanAllMacros()

                OpenCodeApi.sendPromptAsync(
                    address = address,
                    sessionId = sessionId,
                    prompt = prompt,
                    agent = agent
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Bus.emit(
                    ProviderEvent.Error(
                    "Unable to prompt opencode. Is it running in this projects path?"
                ))
                cancel()
            }
        }
    }

    private fun extractAgent(str: String): String? {
        return when {
            str.contains("@a:") -> """@a:(\w+)""".toRegex().find(str)?.groupValues?.getOrNull(1)
            str.contains("@build") -> "build"
            str.contains("@plan") -> "plan"
            else -> null
        }
    }

    private suspend fun ensureConnected(editor: Editor): Boolean {
        if (!isConnected) {
            // Try to auto connect to a server nearest the current file path and its most recent
            // session.
            val filePath = editor.virtualFile?.path ?: return false
            // get all servers
            val nearestServer = OpenCodeApi.getServers()
                .sortedByDescending { it.path.directory.length }
                .firstOrNull { filePath.contains(it.path.directory) } ?: return false

            // Get the most recently updated session
            val session = OpenCodeApi.getSessions(nearestServer.address)
                .getOrNull()
                ?.maxByOrNull { it.time.updated } ?: return false

            connect(nearestServer, session)
        }

        return true
    }

    fun connect(server: OpenCodeApi.Server, session: OpenCodeApi.Session) {
        this.server = server
        this.session = session
        this.isConnected = true
        sseJob?.cancel()
        sseJob = scope.launch {
            OpenCodeApi.getEventsFlow(server.address).collect { event ->
                // Process events
                when (event.type) {
                    "server.instance.disposed" -> {
                        cancel()
                        isConnected = false
                    }
                    "question.asked" -> {
                        Bus.emit(ProviderEvent.Message("OpenCode asked a question"))
                    }
                }
            }
            Bus.emit(ProviderEvent.Message(
                "Connected to ${server.address} for session ${session.title}"
            ))
        }
    }
}


