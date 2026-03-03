package com.hunterstich.idea.jetbridge.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Dispatcher {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Parse the raw prompt for provider routing macros and delegate to the appropriate provider.
     * If no macro is found, fallback to the current default provider and its last used connection.
     */
    fun dispatch(rawPrompt: String, snapshot: ContextSnapshot) {
        scope.launch {
            val routingMatch = """@(oc|gem)(:([a-zA-Z0-9.\-/]+))?""".toRegex().find(rawPrompt)

            val (provider, targetIdOrIndex) = if (routingMatch != null) {
                val handle = routingMatch.groupValues[1]
                val idOrIndex = routingMatch.groupValues[3].takeIf { it.isNotEmpty() }
                val avProvider = AvailableProvider.fromHandle(handle) ?: AvailableProvider.OpenCode
                ConfigStore.getProvider(avProvider.id) to idOrIndex
            } else {
                ConfigStore.provider to null
            }

            val targetId = if (targetIdOrIndex != null) {
                // Try to resolve index to ID. If not found, use as-is (might be a new session ID/name)
                provider.getTarget(targetIdOrIndex)?.id ?: targetIdOrIndex
            } else {
                null
            }

            provider.prompt(rawPrompt, snapshot, targetId)
        }
    }
}
