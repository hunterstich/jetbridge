package com.hunterstich.idea.jetbridge.core

interface Config {
    var providerId: Int
    var openCodeLastAddress: String?
    var openCodeLastSessionId: String?
}

object ConfigStore {

    var config: Config = TmpConfig()
        private set

    fun initialize(config: Config) {
        this.config = config
    }

    private var _provider: Provider? = null
    val provider: Provider
        get() {
            when (config?.providerId) {
                AvailableProvider.GeminiCli.id ->
                    if (_provider !is GeminiCliProvider) _provider = GeminiCliProvider()
                else ->
                    if (_provider !is OpenCodeProvider)  _provider = OpenCodeProvider()
            }
            return _provider!!
        }
}

private class TmpConfig(
    override var providerId: Int = 0,
    override var openCodeLastAddress: String? = null,
    override var openCodeLastSessionId: String? = null
) : Config