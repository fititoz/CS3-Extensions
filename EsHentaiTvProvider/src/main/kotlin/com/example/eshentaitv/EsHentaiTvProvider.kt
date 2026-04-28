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
        "$mainUrl/" to "Últimos Agregados"
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val title = this.selectFirst("h2.h-title")?.text() ?: this.selectFirst("h3.title")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val poster = this.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) mainUrl else "$mainUrl/page/$page/"

        val doc = app.get(url).document
        val home = doc.select(".list-unstyled li a.preview, article.serie").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            list = HomePageList(request.name, home),
            hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select(".list-unstyled li, article.serie").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: ""
        val poster = doc.selectFirst("figure img")?.attr("src")
        val description = doc.selectFirst("p.description")?.text()
        val genres = doc.select("a[href*=/genero/]").map { it.text().trim() }

        val episodes = ArrayList<Episode>()

        for (a in doc.select("ul.h-caps li a")) {
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

        if (episodes.isEmpty()) {
            episodes.add(
                newEpisode(url) {
                    this.name = title
                    this.episode = 1
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
        for (script in doc.select("script")) {
            val scriptData = script.data()
            // Look for base64 encoded URLs or raw URLs
            val base64Regex = Regex("""atob\(['"]([^'"]+)['"]\)""")
            for (match in base64Regex.findAll(scriptData)) {
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
            for (match in urlRegex.findAll(scriptData)) {
                val url = match.groupValues[1]
                if (url.contains("embed") || url.contains("video") || url.contains("player")) {
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        // Extract from onclick attributes
        for (element in doc.select("[onclick]")) {
            val onclick = element.attr("onclick")
            val base64Regex = Regex("""atob\(['"]([^'"]+)['"]\)""")
            for (match in base64Regex.findAll(onclick)) {
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
            for (match in urlRegex.findAll(onclick)) {
                val url = match.groupValues[1]
                if (url.contains("embed") || url.contains("video") || url.contains("player")) {
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
