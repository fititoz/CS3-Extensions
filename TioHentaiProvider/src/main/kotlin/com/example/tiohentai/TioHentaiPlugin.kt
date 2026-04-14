package com.example.tiohentai

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TioHentaiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TioHentaiProvider())
    }
}
