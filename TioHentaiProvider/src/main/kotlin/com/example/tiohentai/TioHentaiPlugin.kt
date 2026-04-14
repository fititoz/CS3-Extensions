package com.example.tiohentai

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TioHentaiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(TioHentaiProvider())
    }
}
