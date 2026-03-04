package com.hunterstich.idea.jetbridge.core

interface Config {
    /**
     * The last [Target] a prompt was sent to. This is used to attempt to set a default target
     * in the prompt dialog if the last taret is still available.
     */
    var lastTargetJson: String?
}

object ConfigStore {

    var config: Config = TmpConfig()
        private set

    fun initialize(config: Config) {
        this.config = config
    }

    private val providers = mutableMapOf<Int, Provider>()

    fun getProvider(id: Int): Provider {
        return providers.getOrPut(id) {
            when (id) {
                AvailableProvider.GeminiCli.id -> GeminiCliProvider()
                else -> OpenCodeProvider()
            }
        }
    }
}

private class TmpConfig(
    override var lastTargetJson: String? = null
) : Config