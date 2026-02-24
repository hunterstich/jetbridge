package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.provider.GeminiCliProvider
import com.hunterstich.idea.jetbridge.provider.OpenCodeProvider
import com.hunterstich.idea.jetbridge.provider.Provider
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
        var selectedProvider = JetbridgeProviderManager.AvailableProvider.OpenCode
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
    private var _provider: Provider? = null

    enum class AvailableProvider(val displayName: String) {
        OpenCode("opencode"),
        GeminiCli("gemini-cli");

        companion object {
            fun fromDisplayName(displayName: String): AvailableProvider {
                return when (displayName) {
                    GeminiCli.displayName -> GeminiCli
                    else -> OpenCode
                }
            }
        }
    }

    val provider: Provider
        get() {
            when (JetbridgeSettings.instance.state.selectedProvider) {
                AvailableProvider.OpenCode ->
                    if (_provider !is OpenCodeProvider)  _provider = OpenCodeProvider()
                AvailableProvider.GeminiCli ->
                    if (_provider !is GeminiCliProvider) _provider = GeminiCliProvider()
            }
            return _provider!!
        }
}
