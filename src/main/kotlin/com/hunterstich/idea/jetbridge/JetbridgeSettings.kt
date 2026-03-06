package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.core.Config
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.EnvironmentUtil

/**
 * Intellij persistent settings class.
 *
 * All access to settings should use [com.hunterstich.idea.jetbridge.core.ConfigStore] - the kotlin
 * interface for this platform-level backer.
 */
@Service(Service.Level.APP)
@State(
    name = "com.hunterstich.idea.jetbridge.JetbridgeSettings",
    storages = [Storage("jetbridge.xml")]
)
internal class JetbridgeSettings : PersistentStateComponent<JetbridgeSettings.State> {
    class State : Config {
        override var lastTargetJson: String? = null
        override val env: Map<String, String> = EnvironmentUtil.getEnvironmentMap()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }
}
