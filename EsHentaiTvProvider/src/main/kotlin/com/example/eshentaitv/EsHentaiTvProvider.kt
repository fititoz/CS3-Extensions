package com.example.eshentaitv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import android.util.Base64

class EsHentaiTvProvider : MainAPI() {

    override var mainUrl = "https://eshentai.tv"
    override var name = "EsHentai.tv"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/#capitulos" to "Últimos Capítulos Actualizados",
        "$mainUrl/#series" to "Últimos Hentai Agregados",
        "$mainUrl/#aleatorio" to "Hentai Aleatorio",
        "$mainUrl/#vistos" to "Más Vistos",
        "$mainUrl/#censura" to "Sin Censura"
    )

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("a") ?: return null
        val title = this.selectFirst("h2.h-title")?.text() ?: this.selectFirst("h3.title")?.text() ?: anchor.attr("title") ?: return null
        var href = fixUrl(anchor.attr("href"))
        
        // Convert episode URLs to series URLs: /slug-1/ -> /hentai-slug.html
        val episodeMatch = Regex("""/([^/]+)-\d+/?$""").find(href)
        if (episodeMatch != null) {
            val slug = episodeMatch.groupValues[1]
            href = "$mainUrl/hentai-$slug.html"
        }
        
        val poster = this.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isFragment = request.data.contains("#")
        val url = if (isFragment) request.data.substringBefore("#") else if (page == 1) request.data else "${request.data}/page/$page"
        val doc = app.get(url).document

        val home = if (isFragment) {
            if (page > 1) return newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
            val fragment = request.data.substringAfter("#")
            val items = when (fragment) {
                "capitulos" -> doc.select("ul.recent-h").firstOrNull()?.select("li a.preview") ?: emptyList()
                "series" -> doc.select("ul.row.m-0.list-unstyled").firstOrNull()?.select("article.serie") ?: emptyList()
                "aleatorio" -> doc.select("ul.row.m-0.list-unstyled").lastOrNull()?.select("article.serie") ?: emptyList()
                "vistos" -> doc.select("h3:contains(Más vistos)").next("ul").select("li")
                "censura" -> doc.select("h3:contains(Sin Censura)").next("ul").select("li")
                else -> doc.select("article.serie")
            }
            items.mapNotNull { it.toSearchResponse() }
        } else {
            doc.select("article.serie").mapNotNull { it.toSearchResponse() }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = !isFragment && home.isNotEmpty()
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

            // Deobfuscate internal proxy URLs
            val serverRegex = Regex("""['"](vk|opp|mail|docs|sen|drc|wish|turbo|your)['"][^;]*?['"](\d+/[a-f0-9]+)['"]""")
            for (match in serverRegex.findAll(scriptData)) {
                val server = match.groupValues[1]
                val id = match.groupValues[2]
                val proxyUrl = "$mainUrl/video/$server/index.php?url=$id"

                try {
                    val proxyUrlWithXxx = proxyUrl.replace("/video/", "/xxx/video/") // fallback to the literal path
                    val responseText = app.get(proxyUrlWithXxx, referer = data).text
                    val proxyDoc = org.jsoup.Jsoup.parse(responseText)
                    val realIframe = proxyDoc.selectFirst("iframe")?.attr("src")
                    
                    val locationRegex = Regex("""location\.replace\(['"](https?://[^'"]+)['"]\)""")
                    val locationMatch = locationRegex.find(responseText)?.groupValues?.get(1)

                    if (realIframe != null && realIframe.startsWith("http")) {
                        if (realIframe.contains("eshentai.tv/xxx/player/")) {
                            // Follow internal player iframe
                            val playerHtml = app.get(realIframe, referer = proxyUrlWithXxx).text
                            // The packed script contains an array with base64 strings like bWVkaWFmaXJlLmNvbQ (mediafire.com)
                            // Look for base64 encoded URLs or hostnames
                            val b64Regex = Regex("""[a-zA-Z0-9+/]{20,}={0,2}""")
                            for (b64 in b64Regex.findAll(playerHtml)) {
                                try {
                                    val decoded = String(Base64.decode(b64.value, Base64.DEFAULT))
                                    if (decoded.contains("mediafire.com") || decoded.startsWith("http")) {
                                        val realUrl = if (decoded.startsWith("http")) decoded else "https://$decoded"
                                        loadExtractor(realUrl, realIframe, subtitleCallback, callback)
                                    }
                                } catch (e: Exception) {}
                            }
                        } else {
                            loadExtractor(realIframe, proxyUrlWithXxx, subtitleCallback, callback)
                        }
                    } else if (locationMatch != null) {
                        loadExtractor(locationMatch, proxyUrlWithXxx, subtitleCallback, callback)
                    } else {
                        // Extract direct mp4 links from JWPlayer or Flashvars or gkplugins
                        val fileRegex = Regex("""(?:file|src|link)["']?\s*[=:]\s*["']?(https?://[^"'\s&<>]+)["']?""")
                        val fileMatch = fileRegex.find(responseText)?.groupValues?.get(1)
                        if (fileMatch != null && !fileMatch.contains("s3.amazonaws.com")) {
                            if (fileMatch.contains(".mp4") || fileMatch.contains(".m3u8") || fileMatch.contains(".mkv")) {
                                callback(
                                    newExtractorLink(
                                        source = name,
                                        name = server.replaceFirstChar { it.uppercase() },
                                        url = fileMatch,
                                    ) {
                                        this.referer = proxyUrlWithXxx
                                        this.quality = Qualities.Unknown.value
                                    }
                                )
                            } else {
                                // It's probably a host embed like Google Drive
                                loadExtractor(fileMatch, proxyUrlWithXxx, subtitleCallback, callback)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            // Look for base64 encoded URLs or raw URLs
            val base64Regex = Regex("""atob\(['"]([^'"]+)['"]\)""")
            for (match in base64Regex.findAll(scriptData)) {
                try {
                    val decoded = String(Base64.decode(match.groupValues[1], Base64.DEFAULT))
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
                    val decoded = String(Base64.decode(match.groupValues[1], Base64.DEFAULT))
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
