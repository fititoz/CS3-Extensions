package com.example.underhentai

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class UnderHentaiProvider : MainAPI() {

    override var mainUrl = "https://www.underhentai.net"
    override var name = "UnderHentai"
    override var lang = "es-MX"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        Pair("$mainUrl/page/", "Latest"),
        Pair("$mainUrl/releases/page/", "Releases"),
        Pair("$mainUrl/uncensored/page/", "Uncensored"),
        Pair("$mainUrl/top/page/", "Top"),
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("h2 a") ?: this.selectFirst("a[href]") ?: return null
        val title = anchor.text().trim()
        if (title.isEmpty()) return null
        val href = fixUrl(anchor.attr("href"))
        val poster = this.selectFirst("img")?.let { img ->
            img.attr("src").ifEmpty { img.attr("data-src") }
        }
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page/"
        val doc = app.get(url).document

        val home = doc.select(".content article").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = doc.select("a[href*='/page/']").any {
                it.text().contains("Next", ignoreCase = true) || it.text().contains("»")
            }
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // WordPress standard search: /?s=query
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select(".content article").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text()
            ?: doc.selectFirst(".article-header h2")?.text()
            ?: ""
        val poster = doc.selectFirst(".content img[src*='static.underhentai.net']")?.attr("src")
            ?: doc.selectFirst(".content img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        val genres = doc.select("a[href*='/tag/']").map { it.text().trim() }

        val episodes = ArrayList<Episode>()

        // Strategy: find all watch links, extract ep number, 
        // read nearby text for language info
        doc.select(".ep2-box").forEach { box ->
            val epName = box.selectFirst(".ep2-header")?.text()?.trim() ?: "Episode"
            
            box.select(".ep2-card").forEach cardLoop@{ card ->
                val streamAnchor = card.selectFirst("a.ep2-stream") ?: return@cardLoop
                val href = fixUrl(streamAnchor.attr("href"))
                val epParam = Regex("""ep=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull() ?: return@cardLoop
                
                val vtype = card.selectFirst(".ep2-vtype")?.text()?.replace(Regex("[^A-Za-z ]"), "")?.trim() ?: ""
                val subsNode = card.select(".ep2-meta-item").find { it.select(".ep2-meta-label").text().contains("Subs") }
                val subs = subsNode?.selectFirst(".ep2-meta-value")?.text()?.replace(Regex("[^A-Za-z ]"), "")?.trim() ?: ""
                
                val langLabel = if (subs.contains("None", true) || subs.isEmpty()) vtype else "$vtype - $subs"
                val finalName = if (langLabel.isNotEmpty()) "$epName ($langLabel)" else epName
                
                if (episodes.none { it.data == href }) {
                    episodes.add(
                        newEpisode(href) {
                            this.name = finalName
                            this.episode = epParam
                        }
                    )
                }
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster?.let { fixUrl(it) }
            addEpisodes(DubStatus.Subbed, episodes.sortedBy { it.episode })
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

        // 1. Direct iframes
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty() && !src.startsWith("about:")) {
                loadExtractor(fixUrl(src), mainUrl, subtitleCallback, callback)
            }
        }

        // 2. Iframes in scripts (lazy-loaded via JS)
        doc.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("iframe") || scriptData.contains("src=")) {
                val iframeRegex = Regex("""(?:src|url)\s*[=:]\s*\\?['"]([^'"\\]+)\\?['"]""")
                iframeRegex.findAll(scriptData).forEach { match ->
                    val url = match.groupValues[1]
                    if (url.startsWith("http") && !url.contains("underhentai.net")) {
                        loadExtractor(url, mainUrl, subtitleCallback, callback)
                    }
                }
            }
        }

        // 3. Look for direct video URLs (mp4, m3u8)
        doc.select("script").forEach { script ->
            val scriptData = script.data()
            val videoRegex = Regex("""(https?://[^'"]+\.(?:mp4|m3u8)[^'"]*)""")
            videoRegex.findAll(scriptData).forEach { match ->
                val videoUrl = match.groupValues[1]
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        return true
    }
}