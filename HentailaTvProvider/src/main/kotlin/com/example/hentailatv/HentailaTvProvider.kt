package com.example.hentailatv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Base64
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
        "$mainUrl/#nuevos" to "Nuevos",
        "$mainUrl/#vistos" to "Lo mas Visto",
        "$mainUrl/#censura" to "Sin Censura",
        "$mainUrl/#incesto" to "Incesto Hentai",
        "$mainUrl/#milfs" to "Milfs",
        "$mainUrl/#yuri" to "Yuri - Lesbianas",
        "$mainUrl/#escolares" to "Escolares",
        "$mainUrl/page/" to "Últimos Agregados"
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a.card__cover") ?: return null
        val title = this.selectFirst(".card__title")?.text() ?: return null
        val href = fixUrl(anchor.attr("href"))
        val poster = anchor.selectFirst("img")?.attr("src")

        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isFragment = request.data.contains("#")
        val url = if (isFragment) request.data.substringBefore("#") else if (page == 1) mainUrl else "$mainUrl/page/$page/"
        val doc = app.get(url).document

        val home = if (isFragment) {
            if (page > 1) return newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
            val fragment = request.data.substringAfter("#")
            val headerText = when (fragment) {
                "nuevos" -> "Nuevos"
                "vistos" -> "Lo mas Visto"
                "censura" -> "Sin Censura"
                "incesto" -> "Incesto Hentai"
                "milfs" -> "Milfs"
                "yuri" -> "Yuri"
                "escolares" -> "Escolares"
                else -> null
            }

            val items = if (headerText != null) {
                doc.select("h3.title:contains($headerText)").firstOrNull()?.parent()?.nextElementSibling()?.select(".item_card") ?: emptyList()
            } else {
                doc.select(".item_card")
            }
            items.mapNotNull { it.toSearchResponse() }
        } else {
            doc.select(".item_card").mapNotNull { it.toSearchResponse() }
        }

        return newHomePageResponse(
            list = HomePageList(request.name, home),
            hasNext = !isFragment && doc.select("div.pagination a").isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select(".item_card").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: ""
        val poster = doc.selectFirst("div.poster img")?.attr("src") ?: doc.selectFirst(".card__cover img")?.attr("src")
        val description = doc.selectFirst("div.wp-content p")?.text() ?: doc.selectFirst("div.description")?.text() ?: doc.selectFirst(".vraven_text.single p")?.text()
        val genres = doc.select("div.sgeneros a").map { it.text().trim() }

        val episodes = ArrayList<Episode>()
        for (a in doc.select("ul.episodios li a, ul.episodes li a, .hentai__episodes ul li.hentai__chapter a")) {
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

        val xToken = doc.selectFirst("meta[name=x-secure-token]")?.attr("content")?.substringAfter("sha512-")
        if (xToken != null) {
            var decoded = xToken
            for (i in 0 until 3) {
                val rot = decoded.map {
                    when {
                        it in 'a'..'z' -> if (it <= 'm') it + 13 else it - 13
                        it in 'A'..'Z' -> if (it <= 'M') it + 13 else it - 13
                        else -> it
                    }
                }.joinToString("")
                decoded = String(android.util.Base64.decode(rot, android.util.Base64.DEFAULT))
            }
            
            val en = Regex(""""en"\s*:\s*"([^"]+)"""").find(decoded)?.groupValues?.get(1)
            val iv = Regex(""""iv"\s*:\s*"([^"]+)"""").find(decoded)?.groupValues?.get(1)
            
            if (en != null && iv != null) {
                val apiResponse = app.post(
                    "$mainUrl/wp-content/plugins/player-logic/api.php",
                    data = mapOf(
                        "action" to "zarat_get_data_player_ajax",
                        "a" to en,
                        "b" to iv
                    ),
                    referer = data
                ).text ?: ""
                
                val urlRegex = Regex("""(https?://[^\\]+?(?:mp4|m3u8|org|com)[^"\\]*)""")
                for (match in urlRegex.findAll(apiResponse)) {
                    val vidUrl = match.groupValues[1]
                    if (vidUrl.contains(".m3u8") || vidUrl.contains(".mp4")) {
                        callback(
                            newExtractorLink(
                                source = name,
                                name = name,
                                url = vidUrl,
                            ) {
                                this.referer = data
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    } else if (vidUrl.contains("octopusmanifest") || vidUrl.contains("anpustream")) {
                        callback(
                            newExtractorLink(
                                source = name,
                                name = "Octopus/Anpu",
                                url = "$vidUrl#.m3u8",
                            ) {
                                this.referer = data
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    } else {
                        loadExtractor(vidUrl, data, subtitleCallback, callback)
                    }
                }
            }
        }

        return true
    }
}
