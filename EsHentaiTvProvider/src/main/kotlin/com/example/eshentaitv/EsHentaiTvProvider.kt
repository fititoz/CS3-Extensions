package com.example.eshentaitv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.Base64

class EsHentaiTvProvider : MainAPI() {

    override var mainUrl = "https://eshentai.tv"
    override var name = "EsHentai.tv"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/emision" to "En Emisión",
        "$mainUrl/series" to "Series",
        "$mainUrl/ovas" to "OVAs",
        "$mainUrl/peliculas" to "Películas"
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a") ?: return null
        val title = this.selectFirst("h3.title")?.text() ?: return null
        val href = fixUrl(anchor.attr("href"))
        val poster = this.selectFirst("figure img")?.attr("src")
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            "${request.data}/page/$page"
        }

        val doc = app.get(url).document
        val home = doc.select("article.serie").mapNotNull { it.toSearchResponse() }

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
        return doc.select("article.serie").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: ""
        val poster = doc.selectFirst("figure img")?.attr("src")
        val description = doc.selectFirst("p.description")?.text()
        val genres = doc.select("a[href*=/genero/]").map { it.text().trim() }

        val episodes = ArrayList<Episode>()

        doc.select("ul.h-caps li a").forEach { a ->
            val href = a.attr("href")
            val epName = a.text().trim()
            val epNum = Regex("""\d+""").find(epName)?.value?.toIntOrNull()
            episodes.add(
                newEpisode(fixUrl(href)) {
                    this.name = epName
                    this.episode = epNum
                }
            )
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster?.let { fixUrl(it) }
            addEpisodes(DubStatus.Subbed, episodes.reversed()) // Usually episodes are listed latest first
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

        // Try to find video container
        val playContainer = doc.selectFirst("div#play-1")
        
        // Extract from script
        doc.select("script").forEach { script ->
            val scriptData = script.data()
            // Look for base64 encoded URLs or raw URLs
            val base64Regex = Regex("""atob\(['"]([^'"]+)['"]\)""")
            base64Regex.findAll(scriptData).forEach { match ->
                try {
                    val decoded = String(Base64.getDecoder().decode(match.groupValues[1]))
                    if (decoded.startsWith("http")) {
                        loadExtractor(decoded, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) {
                    // Ignore decode errors
                }
            }
            
            val urlRegex = Regex("""['"](https?://[^'"]+)['"]""")
            urlRegex.findAll(scriptData).forEach { match ->
                val url = match.groupValues[1]
                if (url.contains("embed") || url.contains("video") || url.contains("player")) {
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        // Extract from onclick attributes
        doc.select("[onclick]").forEach { element ->
            val onclick = element.attr("onclick")
            val base64Regex = Regex("""atob\(['"]([^'"]+)['"]\)""")
            base64Regex.findAll(onclick).forEach { match ->
                try {
                    val decoded = String(Base64.getDecoder().decode(match.groupValues[1]))
                    if (decoded.startsWith("http")) {
                        loadExtractor(decoded, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) {
                    // Ignore decode errors
                }
            }
            
            val urlRegex = Regex("""['"](https?://[^'"]+)['"]""")
            urlRegex.findAll(onclick).forEach { match ->
                val url = match.groupValues[1]
                if (url.contains("embed") || url.contains("video") || url.contains("player")) {
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
