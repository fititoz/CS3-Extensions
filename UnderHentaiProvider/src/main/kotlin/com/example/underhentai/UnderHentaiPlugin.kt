package com.example.underhentai

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class UnderHentaiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(UnderHentaiProvider())
    }
}
