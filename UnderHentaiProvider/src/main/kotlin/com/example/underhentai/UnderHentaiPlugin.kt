package com.example.underhentai

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.GUpload
import com.lagradost.cloudstream3.extractors.LuluStream

@CloudstreamPlugin
class UnderHentaiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(UnderHentaiProvider())
        registerExtractorAPI(GUpload())
        registerExtractorAPI(LuluStream())
    }
}
