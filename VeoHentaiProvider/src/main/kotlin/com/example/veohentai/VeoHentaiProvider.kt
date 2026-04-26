package com.example.veohentai

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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
            it.attr("data-src").takeIf { src -> src.isNotEmpty() } ?: it.attr("src")
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
        val poster = doc.selectFirst("figure img")?.let {
            it.attr("data-src").takeIf { src -> src.isNotEmpty() } ?: it.attr("src")
        }
        val description = doc.selectFirst(".entry-content p")?.text()
        val genres = doc.select("a[href*=/genero/]").map { it.text().trim() }

        val episodes = ArrayList<Episode>()

        // Check if it's a watch page directly or a series page
        val iframe = doc.selectFirst(".aspect-w-16.aspect-h-9 iframe")
        if (iframe != null) {
            // It's a watch page
            episodes.add(
                newEpisode(url) {
                    this.name = title
                    this.episode = 1
                }
            )
        } else {
            // It might be a series page, look for episodes
            doc.select(".grid > a").forEach { a ->
                val href = a.attr("href")
                val epTitle = a.selectFirst("h2")?.text() ?: "Episodio"
                episodes.add(
                    newEpisode(fixUrl(href)) {
                        this.name = epTitle
                    }
                )
            }
        }

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

        for (iframe in doc.select(".aspect-w-16.aspect-h-9 iframe")) {
            val src = iframe.attr("src")
            if (src.isNotEmpty()) {
                loadExtractor(fixUrl(src), data, subtitleCallback, callback)
            }
        }

        return true
    }
}
