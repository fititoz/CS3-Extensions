package com.example.latinohentai

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LatinoHentaiProvider : MainAPI() {
    override var mainUrl = "https://latinohentai.com"
    override var name = "LatinoHentai"
    override val hasMainPage = true
    override var lang = "es"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/#estrenos" to "Próximos Estrenos",
        "$mainUrl/#ultimos" to "Últimos Hentai Agregados",
        "$mainUrl/#recomendaciones" to "Recomendaciones del Día",
        "$mainUrl/episodios/page/" to "Episodios",
        "$mainUrl/hentai/page/" to "Series Hentai"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isFragment = request.data.contains("#")
        val url = if (isFragment) request.data.substringBefore("#") else request.data + page
        val document = app.get(url).document

        val home = if (isFragment) {
            if (page > 1) return newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
            val fragment = request.data.substringAfter("#")
            when (fragment) {
                "estrenos" -> document.select("#featured-titles article").mapNotNull { it.toSearchResult() }
                "ultimos" -> document.select(".items.full article").mapNotNull { it.toSearchResult() }
                "recomendaciones" -> document.select(".dtw_content article").mapNotNull { it.toSearchResult() }
                else -> document.select("article.item").mapNotNull { it.toSearchResult() }
            }
        } else {
            document.select("article.item, article").mapNotNull { it.toSearchResult() }
        }
        
        return newHomePageResponse(HomePageList(request.name, home), hasNext = !isFragment && home.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 a")?.text() ?: this.selectFirst("img")?.attr("alt") ?: return null
        val href = this.selectFirst("h3 a")?.attr("href") ?: this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newAnimeSearchResponse(title, href) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text() ?: throw Exception("No title found")
        val poster = document.selectFirst("img")?.attr("src")
        val description = document.selectFirst("div.description")?.text()

        val episodes = document.select("ul.episodios li a, ul.episodes li a, ul.lista_episodios li a, ul li a").mapNotNull {
            val epHref = it.attr("href")
            val epTitle = it.text()
            if (epHref.contains("/episodio/")) {
                newEpisode(epHref) {
                    this.name = epTitle
                }
            } else null
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Extract iframe or video links
        for (iframe in document.select("iframe")) {
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        // Dooplay admin-ajax.php
        for (li in document.select("ul#playeroptionsul li")) {
            val post = li.attr("data-post")
            val nume = li.attr("data-nume")
            val type = li.attr("data-type")
            
            if (post.isNotBlank() && nume.isNotBlank() && type.isNotBlank()) {
                val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                val response = app.post(
                    ajaxUrl,
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to post,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text
                
                val embedUrl = response.substringAfter("\"embed_url\":\"").substringBefore("\"").replace("\\/", "/")
                if (embedUrl.isNotBlank() && embedUrl.startsWith("http")) {
                    loadExtractor(embedUrl, data, subtitleCallback, callback)
                } else if (response.contains("<iframe")) {
                    val iframeSrc = response.substringAfter("src=\\\"").substringBefore("\\\"").replace("\\/", "/")
                    if (iframeSrc.isNotBlank() && iframeSrc.startsWith("http")) {
                        loadExtractor(iframeSrc, data, subtitleCallback, callback)
                    }
                }
            }
        }
        
        return true
    }
}
