package com.hunterstich.idea.jetbridge.core

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

    // TODO: Update to a list of jobs and connections to support multiple servers
    private var sseJob: Job? = null
    private var sseConnection: Pair<String, String>? = null
    override val displayName: String = AvailableProvider.OpenCode.displayName

    override fun prompt(rawPrompt: String, snapshot: ContextSnapshot, target: Target) {
        scope.launch {
            try {
                val (address, sessionId) = target.connection
                if (!ensureConnected(address, sessionId)) {
                    // TODO: If the session doesn't exist, maybe create one?
                    Bus.emit(
                        ProviderEvent.Error("No running opencode found for the specified target")
                    )
                    cancel()
                    return@launch
                }

                val serverPath = OpenCodeApi.getServerPath(address).getOrNull()?.directory
                if (serverPath == null) {
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
                        "Unable to prompt opencode. Is the specified target running?"
                    )
                )
                cancel()
            }
        }
    }

    override suspend fun getAvailableTargets(): List<Target> {
        val servers = OpenCodeApi.getServers()
        val sessionsWithMeta = servers.flatMap { server ->
            OpenCodeApi.getSessions(server.address).getOrNull()?.map { session ->
                session to server.address
            } ?: emptyList()
        }.sortedByDescending { it.first.time.updated }

        return sessionsWithMeta.map { (session, address) ->
            Target(
                id = "$address/${session.id}",
                label = session.title,
                description = address,
                provider = AvailableProvider.OpenCode,
            )
        }
    }

    private val Target.connection: Pair<String, String>
        get() {
            val parts = id.split("/")
            return parts[0] to parts[1]
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

    private fun ensureConnected(address: String?, sessionId: String?): Boolean {
        if (sseConnection?.first == address && sseConnection?.second == sessionId) return true
        if (address == null || sessionId == null) return false
        val sessions = OpenCodeApi.getSessions(address).getOrNull() ?: return false
        if (!sessions.map { it.id }.contains(sessionId)) {
            return false
        }

        this.sseConnection = address to sessionId
        OpenCodeApi.selectSession(address, sessionId)
        sseJob?.cancel()
        sseJob = scope.launch {
            OpenCodeApi.getEventsFlow(address).collect { event ->
                // Process events
                when (event.type) {
                    "server.instance.disposed" -> {
                        cancel()
                        this@OpenCodeProvider.sseConnection = null
                    }

                    "question.asked" -> {
                        Bus.emit(ProviderEvent.Message("Jetbridge: OpenCode asked a question"))
                    }
                }
            }

        }
        return true
    }
}
