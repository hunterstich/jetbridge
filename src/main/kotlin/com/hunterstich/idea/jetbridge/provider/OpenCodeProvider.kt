package com.hunterstich.idea.jetbridge.provider

import com.hunterstich.idea.jetbridge.JetbridgeSettings
import com.hunterstich.idea.jetbridge.cleanAllMacros
import com.hunterstich.idea.jetbridge.expandInlineMacros
import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeApi
import com.hunterstich.idea.jetbridge.utils.ContextSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementation of Provider for OpenCode
 *
 * To interact with OpenCode, an opencode instance, started with `opencode --port`, must be
 * running somewhere in the path of the project.
 */
class OpenCodeProvider : Provider {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var server: OpenCodeApi.Server? = null
    private var session: OpenCodeApi.Session? = null
    private var sseJob: Job? = null

    override val displayName: String = "opencode"
    override val connectionDesc: String
        get() = session?.title ?: "none"

    override val isConnected: Boolean
        get() = server != null && session != null && sseJob?.isActive == true

    override fun reconnect(projectPath: String?) {
        scope.launch {
            if (!ensureConnected(projectPath)) {
                Bus.emit(ProviderEvent.Error("Jetbridge: No connected opencode session"))
            }
        }
    }

    override fun prompt(rawPrompt: String, snapshot: ContextSnapshot) {
        scope.launch {
            try {
                val filePath: String? = snapshot.filePath
                if (!ensureConnected(filePath)) {
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
                    rawPrompt.expandInlineMacros(serverPath, snapshot)
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

    /**
     * Extract an opencode agent from the prompt string
     *
     * @return a string accepted by opencode's prompt_async endpoint or null if no agent was
     *   specified
     */
    private fun extractAgent(str: String): String? {
        return when {
            str.contains("@a:") -> """@a:(\w+)""".toRegex().find(str)?.groupValues?.getOrNull(1)
            str.contains("@build") -> "build"
            else -> "plan" // Default to using the plan agent
        }
    }

    private fun findSavedConnection(
        servers: List<OpenCodeApi.Server>,
        address: String,
        sessionId: String,
    ): Pair<OpenCodeApi.Server, OpenCodeApi.Session>? {
        val selectedServer = servers.firstOrNull { it.address == address }
        if (selectedServer != null) {
            val selectedSession = OpenCodeApi.getSessions(selectedServer.address)
                .getOrNull()
                ?.firstOrNull { it.id == sessionId }
            if (selectedSession != null) {
                return selectedServer to selectedSession
            }
        }

        servers.forEach { server ->
            val session = OpenCodeApi.getSessions(server.address)
                .getOrNull()
                ?.firstOrNull { it.id == sessionId }
            if (session != null) {
                return server to session
            }
        }
        return null
    }

    private suspend fun ensureConnected(filePath: String?): Boolean {
        if (!isConnected) {
            val servers = OpenCodeApi.getServers()
            if (servers.isEmpty()) return false

            val lastAddress = JetbridgeSettings.instance.state.openCodeLastAddress
            val lastSessionId = JetbridgeSettings.instance.state.openCodeLastSessionId
            if (lastAddress != null && lastSessionId != null) {
                val savedConnection = findSavedConnection(servers, lastAddress, lastSessionId)
                if (savedConnection != null) {
                    connect(savedConnection.first, savedConnection.second)
                    return true
                }
            }

            // Fall back to nearest server for current file path and its most recent session.
            val nearestServer = if (filePath == null) {
                // If there isn't a file path, there isn't a way to judge path nearness. Simply
                // use the first server in the list.
                servers.firstOrNull()
            } else {
                servers
                    .sortedByDescending { it.path.directory.length }
                    .firstOrNull { filePath.contains(it.path.directory) }
            } ?: return false

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
        JetbridgeSettings.instance.state.openCodeLastAddress = server.address
        JetbridgeSettings.instance.state.openCodeLastSessionId = session.id

        OpenCodeApi.selectSession(server.address, session.id)
        sseJob?.cancel()
        sseJob = scope.launch {
            OpenCodeApi.getEventsFlow(server.address).collect { event ->
                // Process events
                when (event.type) {
                    "server.instance.disposed" -> {
                        cancel()
                        this@OpenCodeProvider.server = null
                        this@OpenCodeProvider.session = null
                    }
                    "question.asked" -> {
                        Bus.emit(ProviderEvent.Message("Jetbridge: OpenCode asked a question"))
                    }
                }
            }

        }
        scope.launch {
            Bus.emit(ProviderEvent.Status(
                "Jetbridge: Connected to opencode session \"$connectionDesc\""
            ))
        }
    }
}
