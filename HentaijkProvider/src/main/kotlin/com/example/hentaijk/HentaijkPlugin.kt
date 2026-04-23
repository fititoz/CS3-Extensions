package com.example.hentaijk

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class HentaijkPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(HentaijkProvider())
    }
}
