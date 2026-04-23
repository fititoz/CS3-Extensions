package com.example.hentaila

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class HentailaPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(HentailaProvider())
    }
}
