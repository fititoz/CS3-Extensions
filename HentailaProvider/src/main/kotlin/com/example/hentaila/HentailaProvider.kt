package com.example.hentaila

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HentailaProvider : MainAPI() {

    override var mainUrl = "https://hentaila.com"
    override var name = "Hentaila"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "$mainUrl/catalogo" to "Catálogo",
        "$mainUrl/catalogo?genre=milfs" to "Milfs",
        "$mainUrl/catalogo?genre=romance" to "Romance",
        "$mainUrl/catalogo?genre=escolares" to "Escolares",
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a[href*=/media/]") ?: return null
        val title = this.selectFirst("h3")?.text() ?: return null
        val href = fixUrl(anchor.attr("href"))
        val poster = this.selectFirst("img")?.attr("src")
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("?")) {
            "${request.data}&page=$page"
        } else {
            "${request.data}?page=$page"
        }

        val doc = app.get(url).document

        val home = doc.select("article").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/catalogo?search=$query").document
        return doc.select("article").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: ""
        val poster = doc.selectFirst("img[src*=cdn.hentaila.com]")?.attr("src")
        val description = doc.selectFirst("p.sinopsis, div.synopsis p, div.description p")?.text()
        val genres = doc.select("a[href*=genre=]").map { it.text().trim() }

        val slug = url.trimEnd('/').substringAfterLast("/")
        val episodes = ArrayList<Episode>()

        // Try to find episode links directly in HTML
        doc.select("a[href*='/media/']").forEach { a ->
            val href = a.attr("href")
            val epMatch = Regex("""/media/[^/]+/(\d+)""").find(href)
            if (epMatch != null) {
                val epNum = epMatch.groupValues[1].toIntOrNull() ?: return@forEach
                episodes.add(
                    newEpisode(fixUrl(href)) {
                        this.name = "Episodio $epNum"
                        this.episode = epNum
                    }
                )
            }
        }

        // Fallback: parse SvelteKit script data for episodes
        if (episodes.isEmpty()) {
            doc.select("script").forEach { script ->
                val scriptData = script.data()
                if (scriptData.contains("episodes") && scriptData.contains("number")) {
                    val epRegex = Regex(""""number"\s*:\s*(\d+)""")
                    epRegex.findAll(scriptData).forEach { match ->
                        val epNum = match.groupValues[1].toIntOrNull() ?: return@forEach
                        val link = "$mainUrl/media/$slug/$epNum"
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

        return newAnimeLoadResponse(title, url, TvType.NSFW) {
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

        doc.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("embeds") || scriptData.contains("url")) {
                val embedRegex = Regex(""""url"\s*:\s*"(https?://[^"]+)"""")
                embedRegex.findAll(scriptData).forEach { match ->
                    val url = match.groupValues[1].replace("\\/", "/")
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
