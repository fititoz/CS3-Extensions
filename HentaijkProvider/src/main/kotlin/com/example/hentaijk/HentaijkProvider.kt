package com.example.hentaijk

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HentaijkProvider : MainAPI() {

    override var mainUrl = "https://hentaijk.com"
    override var name = "HentaiJK"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    // --- Helper: Convert search-result card (div.anime__item) to SearchResponse ---
    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a") ?: return null
        val href = fixUrl(anchor.attr("href"))
        val poster = this.selectFirst("div.anime__item__pic")?.attr("data-setbg")
            ?: this.selectFirst("div[data-setbg]")?.attr("data-setbg")
        val title = this.selectFirst("div.anime__item__text h5 a")?.text()
            ?: this.selectFirst("h5")?.text()
            ?: anchor.attr("title")
            ?: return null
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    // --- Helper: Convert homepage card (div.card) to SearchResponse ---
    private fun Element.toHomePageCard(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a[href]") ?: return null
        val episodeUrl = fixUrl(anchor.attr("href"))
        // Strip episode number from URL: /slug/3/ -> /slug/
        val animeUrl = episodeUrl.trimEnd('/').substringBeforeLast("/") + "/"
        val title = this.selectFirst("h5.card-title")?.text() ?: return null
        val poster = this.selectFirst("img.card-img-top")?.attr("data-animepic")
        return newAnimeSearchResponse(title, animeUrl) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    // --- Helper: Convert sidebar recent-series card to SearchResponse ---
    private fun Element.toRecentCard(): AnimeSearchResponse? {
        val anchor = this.selectFirst("h5.card-title a") ?: this.selectFirst("a[href]") ?: return null
        val href = fixUrl(anchor.attr("href"))
        val title = anchor.text().trim()
        if (title.isEmpty()) return null
        val poster = this.selectFirst("img")?.attr("src")
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    // --- Helper: Convert top-anime card to SearchResponse ---
    private fun Element.toTopCard(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a[href]") ?: return null
        val href = fixUrl(anchor.attr("href"))
        val title = this.selectFirst("h5.card-title")?.text()?.trim() ?: return null
        val poster = this.selectFirst("div.card-img img")?.attr("src")
            ?: this.selectFirst("img.card-img-top")?.attr("src")
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    // --- MainPage: sections from homepage ---
    override val mainPage = mainPageOf(
        "$mainUrl/#animes" to "Últimos Episodios",
        "$mainUrl/#ovas" to "OVAs & Especiales",
        "$mainUrl/#recientes" to "Hentai Recientes",
        "$mainUrl/#top" to "Top Animes",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Homepage doesn't paginate, only show first page
        if (page > 1) return newHomePageResponse(
            list = HomePageList(name = request.name, list = emptyList()),
            hasNext = false
        )

        val url = request.data.substringBefore("#")
        val doc = app.get(url).document
        val fragment = request.data.substringAfter("#", "")

        val home = when (fragment) {
            "animes" -> {
                doc.selectFirst("#animes")?.select("div.card")?.mapNotNull { it.toHomePageCard() } ?: emptyList()
            }
            "ovas" -> {
                doc.selectFirst("#ovas")?.select("div.card")?.mapNotNull { it.toHomePageCard() } ?: emptyList()
            }
            "recientes" -> {
                val container = doc.select("div.trending_div").firstOrNull {
                    it.selectFirst("h4")?.text()?.contains("recientes", ignoreCase = true) == true
                }
                container?.select("div.p-3.d-flex")?.mapNotNull { it.toRecentCard() } ?: emptyList()
            }
            "top" -> {
                doc.select("div.col.toplist div.card").mapNotNull { it.toTopCard() }
            }
            else -> {
                doc.select("div.card").mapNotNull { it.toHomePageCard() }
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = false
        )
    }

    // --- Search: /buscar?q={query} ---
    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/buscar?q=$query").document
        return doc.select("div.anime__item").mapNotNull { it.toSearchResponse() }
    }

    // --- Load: Detail page with episodes ---
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h3")?.text() ?: ""
        val poster = doc.selectFirst("div[data-setbg]")?.attr("data-setbg")
            ?: doc.selectFirst("div.anime__details__pic")?.attr("data-setbg")
        val description = doc.selectFirst("p.tab.sinopsis")?.text()
            ?: doc.selectFirst("div.sinopsis p")?.text()
        val genres = doc.select("a[href*=\"/genero/\"]").map { it.text().trim() }
        val status = when {
            doc.select(".enemision").text().contains("Finalizado") -> ShowStatus.Completed
            doc.select(".enemision").text().contains("En emisión") -> ShowStatus.Ongoing
            else -> null
        }

        val slug = url.trimEnd('/').substringAfterLast("/")
        val episodes = ArrayList<Episode>()

        // 1. Extract anime ID from data-anime attribute (used for AJAX calls)
        val animeId = doc.selectFirst("div[data-anime]")?.attr("data-anime")

        // 2. Extract total episode count from the metadata list
        val epCountLabel = doc.select("li").find { it.text().contains("Episodios:", true) }
        val totalEps = epCountLabel?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0

        if (totalEps > 0) {
            for (i in 1..totalEps) {
                episodes.add(
                    newEpisode("$mainUrl/$slug/$i/") {
                        this.name = "Episodio $i"
                        this.episode = i
                    }
                )
            }
        }

        // Try to extract episodes from page links matching /{slug}/{number}
        val epRegex = Regex("""/$slug/(\d+)""")
        doc.select("a[href]").forEach { a ->
            val href = a.attr("href")
            val match = epRegex.find(href)
            if (match != null) {
                val epNum = match.groupValues[1].toIntOrNull() ?: return@forEach
                val link = fixUrl(href)
                if (episodes.none { it.episode == epNum }) {
                    episodes.add(
                        newEpisode(link) {
                            this.name = "Episodio $epNum"
                            this.episode = epNum
                        }
                    )
                }
            }
        }

        // Fallback: try inline JS for episode data
        if (episodes.isEmpty()) {
            doc.select("script").forEach { script ->
                val scriptData = script.data()
                // Look for AJAX pattern with anime ID
                val idMatch = Regex("""/ajax/last_episode/(\d+)/""").find(scriptData)
                    ?: Regex("""episodes/(\d+)/""").find(scriptData)
                if (idMatch != null) {
                    val lastEpMatch = Regex("""lastEpisode\s*=\s*(\d+)""").find(scriptData)
                    val totalEps = lastEpMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                    for (epNum in 1..totalEps) {
                        val link = "$mainUrl/$slug/$epNum"
                        episodes.add(
                            newEpisode(link) {
                                this.name = "Episodio $epNum"
                                this.episode = epNum
                            }
                        )
                    }
                }
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster?.let { fixUrl(it) }
            addEpisodes(DubStatus.Subbed, episodes.sortedBy { it.episode })
            showStatus = status
            plot = description
            tags = genres
        }
    }

    // --- LoadLinks: Extract video URLs from episode page ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        doc.select("script").forEach { script ->
            val scriptData = script.data()

            // Extract video[] iframe sources
            if (scriptData.contains("var video = [];")) {
                val iframeRegex = Regex("""src=\\?"([^"\\]+)\\?"""")
                iframeRegex.findAll(scriptData).forEach { match ->
                    val iframeUrl = if (match.groupValues[1].startsWith("/")) fixUrl(match.groupValues[1]) else match.groupValues[1]
                    when {
                        "jkplayer/jk" in iframeUrl && "jkmedia" in iframeUrl -> {
                            val streamUrl = iframeUrl.substringAfter("u=")
                            val fullUrl = "$mainUrl/$streamUrl"
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "Xtreme S",
                                    url = fullUrl,
                                ) {
                                    this.referer = mainUrl
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                        }
                        "jkplayer/um" in iframeUrl || "jkplayer/umv" in iframeUrl -> {
                            try {
                                val iframeDoc = app.get(iframeUrl, referer = mainUrl).document
                                iframeDoc.select("script").forEach { s ->
                                    val d = s.data()
                                    if (d.contains("url:") || d.contains("var parts = {") || d.contains("player.setup")) {
                                        val videoUrl = Regex("""url:\s*\\?['"]([^'"\\]+)\\?['"]""").find(d)?.groupValues?.get(1)
                                        if (videoUrl != null) {
                                            callback(
                                                newExtractorLink(
                                                    source = name,
                                                    name = "Sabrosio",
                                                    url = videoUrl,
                                                ) {
                                                    this.referer = mainUrl
                                                    this.quality = Qualities.Unknown.value
                                                }
                                            )
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        else -> {
                            // Generic embed URL - try loadExtractor
                            loadExtractor(iframeUrl, mainUrl, subtitleCallback, callback)
                        }
                    }
                }
            }

            // Extract from servers array (download links with base64-encoded URLs)
            if (scriptData.contains("var servers = [")) {
                try {
                    val serversJson = scriptData.substringAfter("var servers = ").substringBefore(";")
                    val serverRegex = Regex(""""remote"\s*:\s*"([^"]+)"""")
                    serverRegex.findAll(serversJson).forEach { remoteMatch ->
                        val encoded = remoteMatch.groupValues[1]
                        val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
                        loadExtractor(decoded, mainUrl, subtitleCallback, callback)
                    }
                } catch (_: Exception) {}
            }
        }

        return true
    }
}
