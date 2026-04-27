package com.example.veohentai

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Base64
import org.jsoup.nodes.Element

class VeoHentaiProvider : MainAPI() {

    override var mainUrl = "https://veohentai.com"
    override var name = "VeoHentai"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Inicio",
        "$mainUrl/genero/3d/" to "3D",
        "$mainUrl/genero/sin-censura/" to "Sin Censura",
        "$mainUrl/genero/yuri/" to "Yuri",
        "$mainUrl/genero/milf/" to "Milf",
        "$mainUrl/genero/incesto/" to "Incesto"
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val href = fixUrl(this.attr("href"))
        val title = this.selectFirst("h2")?.text() ?: return null
        val poster = this.selectFirst("figure img")?.let {
            it.attr("src").ifEmpty { it.attr("data-src") }
        }
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            if (request.data == "$mainUrl/") {
                "$mainUrl/page/$page/"
            } else {
                "${request.data}page/$page/"
            }
        }

        val doc = app.get(url).document
        val home = doc.select("#posts-home > a, .grid > a").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("#posts-home > a, .grid > a").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: ""
        val poster = doc.selectFirst("figure img")?.attr("src")
        val description = doc.selectFirst(".entry-content p")?.text()
        val genres = doc.select("a[href*=/genero/]").map { it.text().trim() }

        val episodes = listOf(
            newEpisode(url) {
                this.name = title
                this.episode = 1
            }
        )

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster?.let { fixUrl(it) }
            addEpisodes(DubStatus.Subbed, episodes)
            plot = description
            tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // Veohentai uses multiple servers in the player section
        // Check for iframes directly inside .aspect-w-16.aspect-h-9 or similar
        for (iframe in doc.select("iframe, .aspect-w-16 iframe")) {
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.contains("hentaiplayer.com")) {
                val iframeDoc = app.get(src).document
                val dataId = iframeDoc.selectFirst("li[data-id]")?.attr("data-id") ?: continue
                val vidParam = Regex("""vid=([^&]+)""").find(dataId)?.groupValues?.get(1) ?: continue
                val decoded = String(Base64.decode(vidParam, Base64.DEFAULT))
                val mp4Url = decoded.split("|").firstOrNull() ?: continue
                if (mp4Url.startsWith("http")) {
                    callback(
                        newExtractorLink(
                            name,
                            "HentaiPlayer",
                            mp4Url,
                            referer = src,
                            quality = Qualities.Unknown.value,
                            isM3u8 = mp4Url.contains(".m3u8")
                        )
                    )
                }
                continue
            }
            if (src.isNotBlank() && !src.contains("youtube")) {
                loadExtractor(fixUrl(src), data, subtitleCallback, callback)
            }
        }

        // Check for scripts holding video links (common in WordPress)
        for (script in doc.select("script")) {
            val dataContent = script.data()
            if (dataContent.contains("iframe") || dataContent.contains("src=")) {
                val iframeRegex = Regex("""src=\\?['"](https?://[^'"\\]+)\\?['"]""")
                for (match in iframeRegex.findAll(dataContent)) {
                    val url = match.groupValues[1]
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}
