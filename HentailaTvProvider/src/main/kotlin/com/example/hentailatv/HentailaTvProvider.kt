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
        "$mainUrl/episodios/page/" to "Episodios",
        "$mainUrl/hentai/page/" to "Hentai"
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val title = this.selectFirst("h3 a")?.text() ?: this.selectFirst("img")?.attr("alt") ?: return null
        val href = this.selectFirst("h3 a")?.attr("href") ?: this.selectFirst("a")?.attr("href") ?: return null
        val poster = this.selectFirst("img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } }

        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page/"
        val doc = app.get(url).document
        val home = doc.select("article.item").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = doc.select("div.pagination a").isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("article.item").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: ""
        val poster = doc.selectFirst("div.poster img")?.attr("src")
        val description = doc.selectFirst("div.wp-content p")?.text() ?: doc.selectFirst("div.description")?.text()
        val genres = doc.select("div.sgeneros a").map { it.text().trim() }

        val episodes = ArrayList<Episode>()
        for (a in doc.select("ul.episodios li a, ul.episodes li a")) {
            val href = a.attr("href")
            val epName = a.text().trim()
            val epNum = Regex("""(\d+)""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            episodes.add(
                newEpisode(fixUrl(href)) {
                    this.name = epName
                    this.episode = epNum
                }
            )
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
