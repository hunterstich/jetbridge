package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.GeminiCliProvider
import com.hunterstich.idea.jetbridge.provider.Provider
import com.hunterstich.idea.jetbridge.provider.opencode.OpenCodeProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "com.hunterstich.idea.jetbridge.JetbridgeSettings",
    storages = [Storage("jetbridge.xml")]
)
class JetbridgeSettings : PersistentStateComponent<JetbridgeSettings.State> {
    class State {
        var providerType: String = "opencode"
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val instance: JetbridgeSettings
            get() = ApplicationManager.getApplication().getService(JetbridgeSettings::class.java)
    }
}

object JetbridgeProviderManager {
    private val providers = mapOf(
        "opencode" to OpenCodeProvider(),
        "gemini-cli" to GeminiCliProvider()
    )

    val provider: Provider
        get() = providers[JetbridgeSettings.instance.state.providerType] ?: providers["opencode"]!!

    fun getProvider(type: String): Provider? = providers[type]

    fun getAllProviderTypes(): List<String> = providers.keys.toList()
}
