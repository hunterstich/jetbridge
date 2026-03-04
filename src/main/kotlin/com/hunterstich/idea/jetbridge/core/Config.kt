package com.hunterstich.idea.jetbridge.core

interface Config {
    var providerId: Int
    var openCodeLastAddress: String?
    var openCodeLastSessionId: String?
    var geminiCliLastSessionName: String?
}

object ConfigStore {

    var config: Config = TmpConfig()
        private set

    fun initialize(config: Config) {
        this.config = config
    }

    private val providers = mutableMapOf<Int, Provider>()

    val provider: Provider
        get() = getProvider(config.providerId)

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
    override var providerId: Int = 0,
    override var openCodeLastAddress: String? = null,
    override var openCodeLastSessionId: String? = null,
    override var geminiCliLastSessionName: String? = null,
) : Config