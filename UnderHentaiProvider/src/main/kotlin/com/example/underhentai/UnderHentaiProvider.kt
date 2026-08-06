package com.example.underhentai

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class UnderHentaiProvider : MainAPI() {

    override var mainUrl = "https://www.underhentai.net"
    override var name = "UnderHentai"
    override var lang = "multi"
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
        val anchor = this.selectFirst("a[href]") ?: return null
        val title = this.selectFirst("h3")?.text()?.trim() ?: return null
        if (title.isEmpty()) return null
        val href = fixUrl(anchor.attr("href"))
        val img = this.selectFirst("img")
        val poster = img?.attr("src")?.ifEmpty { img.attr("srcset").substringBefore(" ") }?.ifEmpty { img.attr("data-src") }
        
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page/"
        val doc = app.get(url).document

        val home = doc.select("article.post-card").mapNotNull { it.toSearchResponse() }

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
        return doc.select("article.post-card").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h2.text-truncate-2")?.text()?.trim() ?: ""
        val poster = doc.selectFirst("aside.post-sidebar a.glightbox")?.attr("href")?.ifEmpty { null }
            ?: doc.selectFirst("aside.post-sidebar img")?.attr("src")
        val description = doc.selectFirst("div.row-desc p")?.text()?.trim()
        val genres = doc.select("ul.tags-list li a.label").map { it.text().trim() }

        val episodes = ArrayList<Episode>()

        val epBoxes = doc.select(".ep-box")
        for (box in epBoxes) {
            val epName = box.selectFirst(".ep-header")?.text()?.trim() ?: "Episode"
            
            val links = box.select("a.actions-value[href*='/watch/']")
            for (a in links) {
                val href = fixUrl(a.attr("href"))
                val epParam = Regex("""ep=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                
                val actions = a.closest(".variant-actions")
                val meta = actions?.previousElementSibling()
                
                val subsItem = meta?.select(".meta-item")?.find { it.text().contains("Subs", ignoreCase = true) }
                val subsValue = subsItem?.selectFirst(".meta-value, span")?.text()?.trim() ?: ""
                
                val subLabel = when {
                    subsValue.contains("English", ignoreCase = true) -> "SUB ENG"
                    subsValue.contains("Spanish", ignoreCase = true) -> "SUB ESP"
                    subsValue.contains("None", ignoreCase = true) || subsValue.isEmpty() -> "RAW"
                    else -> "SUB ${subsValue.uppercase().take(3)}"
                }
                
                val finalName = "$epName [$subLabel]"
                
                if (episodes.none { it.data == href }) {
                    episodes.add(
                        newEpisode(href) {
                            this.name = finalName
                            if (epParam != null) {
                                this.episode = epParam
                            }
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
        val doc = app.get(data, referer = data).document

        // 1. Direct iframes
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotEmpty() && !src.startsWith("about:")) {
                loadExtractor(fixUrl(src), data, subtitleCallback, callback)
            }
        }

        // 2. Iframes in scripts (lazy-loaded via JS)
        doc.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("iframe") || scriptData.contains("src=") || scriptData.contains("data-src=")) {
                val iframeRegex = Regex("""(?:data-src|src|url)\s*[=:]\s*\\?['"]([^'"\\]+)\\?['"]""")
                iframeRegex.findAll(scriptData).forEach { match ->
                    val url = match.groupValues[1]
                    if (url.startsWith("http") && !url.contains("underhentai.net")) {
                        loadExtractor(url, data, subtitleCallback, callback)
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
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        return true
    }
}