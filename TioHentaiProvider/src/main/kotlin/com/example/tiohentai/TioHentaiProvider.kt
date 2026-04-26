package com.example.tiohentai

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.nodes.Element

class TioHentaiProvider : MainAPI() {

    override var mainUrl = "https://tiohentai.com"
    override var name = "TioHentai"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    // --- Data classes for search API response ---
    data class SearchObject(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("last_id") val lastId: String?,
        @JsonProperty("slug") val slug: String
    )

    // --- Helper: Convert directory card Element to SearchResponse ---
    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a") ?: return null
        val title = this.selectFirst("h3.title")?.text() ?: return null
        val href = fixUrl(anchor.attr("href"))
        val poster = this.selectFirst("figure img")?.attr("src")
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    // --- MainPage: 4 sections ---
    override val mainPage = mainPageOf(
        Pair("$mainUrl/", "Últimos Episodios"),
        Pair("$mainUrl/directorio", "Últimos Hentai"),
        Pair("$mainUrl/directorio?censura=no", "Sin Censura"),
        Pair("$mainUrl/directorio?genero=milf", "Popular: Milf"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("/directorio")) {
            if (request.data.contains("?")) {
                "${request.data}&p=$page"
            } else {
                "${request.data}?p=$page"
            }
        } else {
            request.data // Homepage doesn't paginate
        }

        val doc = app.get(url).document

        val home = if (request.data == "$mainUrl/") {
            // Homepage: latest episodes use different selectors + URL rewriting
            doc.select("ul.episodes li article").mapNotNull { element ->
                val anchor = element.selectFirst("a") ?: return@mapNotNull null
                val title = element.selectFirst("h3.title")?.text()
                    ?.replace(Regex("(\\d+)$"), "")?.trim() ?: return@mapNotNull null
                val poster = element.selectFirst("figure img")?.attr("src")
                val epRegex = Regex("-(\\d+)$")
                val rawHref = anchor.attr("href")
                // Convert /ver/slug-N to /hentai/slug
                val href = rawHref.replace(epRegex, "").replace("/ver/", "/hentai/")
                val epNum = epRegex.find(rawHref)?.groupValues?.get(1)?.toIntOrNull()
                newAnimeSearchResponse(title, fixUrl(href)) {
                    this.posterUrl = poster?.let { fixUrl(it) }
                    addDubStatus(DubStatus.Subbed, epNum)
                }
            }
        } else {
            // Directory pages: standard cards
            doc.select("ul.animes li article").mapNotNull { it.toSearchResponse() }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = doc.select("nav ul.pagination li.page-item:not(.disabled) a[rel=next]").isNotEmpty()
        )
    }

    // --- Search: POST /api/search ---
    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/api/search",
            data = mapOf("value" to query)
        ).text
        val json = parseJson<List<SearchObject>>(response)
        return json.map { item ->
            newAnimeSearchResponse(item.title, "$mainUrl/hentai/${item.slug}") {
                this.posterUrl = "$mainUrl/uploads/portadas/${item.id}.jpg"
            }
        }
    }

    // --- Load: Detail page with episodes ---
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.title")?.text() ?: ""
        val poster = doc.selectFirst("aside .thumb figure img")?.attr("src")
        val description = doc.selectFirst("p.sinopsis")?.text()
        val genres = doc.select("p.genres a").map { it.text().trim() }
        val status = when (doc.selectFirst(".thumb a.btn.status")?.text()?.trim()) {
            "Finalizado" -> ShowStatus.Completed
            "En emision" -> ShowStatus.Ongoing
            else -> null
        }

        // Extract episodes from inline JS: var episodes = [2,1];
        val episodes = ArrayList<Episode>()
        val slug = url.substringAfterLast("/")

        doc.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("var episodes = [")) {
                val data = scriptData
                    .substringAfter("var episodes = [")
                    .substringBefore("];")
                data.split(",").forEach { epNumStr ->
                    val epNum = epNumStr.trim().toIntOrNull() ?: return@forEach
                    val link = "$mainUrl/ver/$slug-$epNum"
                    episodes.add(
                        newEpisode(link) {
                            this.name = "Episodio $epNum"
                            this.episode = epNum
                        }
                    )
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
            if (scriptData.contains("var videos =")) {
                val videosStr = scriptData.replace("\\/", "/")
                fetchUrls(videosStr).toList().forEach { url ->
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
