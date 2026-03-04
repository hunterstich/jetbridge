package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.core.AvailableProvider
import com.hunterstich.idea.jetbridge.core.Config
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

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
        override var providerId = AvailableProvider.OpenCode.id
        /** OpenCode settings **/
        override var openCodeLastAddress: String? = null
        override var openCodeLastSessionId: String? = null
        override var geminiCliLastSessionName: String? = null
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }
}
