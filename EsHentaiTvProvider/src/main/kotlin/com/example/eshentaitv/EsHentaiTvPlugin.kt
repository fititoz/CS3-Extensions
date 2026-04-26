package com.example.eshentaitv

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class EsHentaiTvPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(EsHentaiTvProvider())
    }
}
