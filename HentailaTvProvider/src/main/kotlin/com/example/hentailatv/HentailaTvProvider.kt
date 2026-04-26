package com.example.hentailatv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HentailaTvProvider : MainAPI() {

    override var mainUrl = "https://hentaila.tv"
    override var name = "Hentaila.tv"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/directorio" to "Directorio"
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a.card__cover") ?: return null
        val title = this.selectFirst(".card__title")?.text() ?: return null
        val href = fixUrl(anchor.attr("href"))
        val poster = this.selectFirst("a.card__cover img")?.attr("src")
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
        val home = doc.select(".item_card").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/buscar?q=$query").document
        return doc.select(".item_card").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: ""
        val poster = doc.selectFirst(".card__cover img")?.attr("src")
        val description = doc.selectFirst(".vraven_text.single p")?.text()
        val genres = doc.select(".card__genres a").map { it.text().trim() }

        val episodes = doc.select(".hentai__episodes ul li.hentai__chapter a").mapNotNull { a ->
            val href = a.attr("href")
            val epName = a.text().trim()
            val epNum = Regex("""(\d+)""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            newEpisode(fixUrl(href)) {
                this.name = epName
                this.episode = epNum
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster?.let { fixUrl(it) }
            addEpisodes(DubStatus.Subbed, episodes.reversed())
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

        for (iframe in doc.select(".player_logic_item iframe")) {
            val url = iframe.attr("src")
            if (url.isNotBlank()) {
                loadExtractor(fixUrl(url), data, subtitleCallback, callback)
            }
        }

        return true
    }
}
