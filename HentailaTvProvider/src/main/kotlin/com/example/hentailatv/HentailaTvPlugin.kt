package com.example.hentailatv

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class HentailaTvPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(HentailaTvProvider())
    }
}
