# Plan: UnderHentai CloudStream3 Extension Provider

## Objective
Create a new CloudStream3 extension provider for `https://www.underhentai.net/` following the exact patterns of the existing providers (HentaijkProvider, HentailaProvider, TioHentaiProvider).

## Scope
**IN:**
- `UnderHentaiProvider/build.gradle.kts` — plugin metadata
- `UnderHentaiProvider/src/main/kotlin/com/example/underhentai/UnderHentaiPlugin.kt` — plugin loader
- `UnderHentaiProvider/src/main/kotlin/com/example/underhentai/UnderHentaiProvider.kt` — main provider
- Update `settings.gradle.kts` — add `"UnderHentaiProvider"` to includes

**OUT:**
- No changes to existing providers
- No changes to root `build.gradle.kts`
- No new extractors (use `loadExtractor` for known hosts)

## Architecture Reference
```
Root build.gradle.kts auto-derives namespace:
  namespace = "com.example.${project.name.lowercase().replace("provider", "")}"
  → "com.example.underhentai"

Package: com.example.underhentai
Base class: MainAPI()
Type: TvType.NSFW
Language: "es" (site is English but follows project convention)
```

## Site Structure Map (underhentai.net)
```
Homepage (/)
  → article cards: .content article
    → title: article h2 a[href]  (href = /{slug}/)
    → poster: article img[src]   (from static.underhentai.net/uploads/)
  → pagination: /page/N/  (links: a[href*="/page/"])

Index (/index/)
  → alphabetical listing, same article card format

Releases (/releases/)
  → latest releases, same article card format

Uncensored (/uncensored/)
  → filtered listing, same article card format

Top (/top/)
  → top rated, same article card format

Genres (/tag/{genre}/)
  → filtered by genre, same article card format

Detail page (/{slug}/)
  → title: h1 or .article-header h2
  → poster: main img (src from static.underhentai.net/uploads/)
  → genres: a[href*="/tag/"]
  → brand: a[href*="/cat/brand/"]
  → episodes: grouped by "Episode NN" headers
    → each episode has stream link: /watch/?id={id}&ep={num}
    → Watch link text: "Watch" with href /watch/?id=X&ep=Y

Watch/Stream page (/watch/?id=X&ep=Y)
  → server tabs: KrakenFiles, LuluStream, Byse
  → video loaded via iframe (lazy/JS-rendered)
  → iframe src contains the actual video embed URL
  → some servers may require login (MEGA)

Search
  → navbar form with input, likely submits to /?s={query} (WordPress standard)
  → OR custom endpoint - implementer must test GET /?s=query and observe results
  → results use same article card format
```

## Tasks

### Task 1: Create `UnderHentaiProvider/build.gradle.kts`
**File:** `UnderHentaiProvider/build.gradle.kts` (NEW)
**Action:** Create file with this exact content:
```kotlin
version = 1

cloudstream {
    language = "es"
    description = "Hentai streaming and downloads from UnderHentai"
    authors = listOf("User")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1

    tvTypes = listOf(
        "NSFW"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=underhentai.net&sz=%size%"
}
```
**QA:** Verify file matches the pattern of `HentaijkProvider/build.gradle.kts` exactly (same structure, different values).

### Task 2: Create `UnderHentaiPlugin.kt`
**File:** `UnderHentaiProvider/src/main/kotlin/com/example/underhentai/UnderHentaiPlugin.kt` (NEW)
**Action:** Create file with this exact content:
```kotlin
package com.example.underhentai

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class UnderHentaiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(UnderHentaiProvider())
    }
}
```
**QA:** Verify package is `com.example.underhentai`, class name is `UnderHentaiPlugin`, registers `UnderHentaiProvider()`.

### Task 3: Create `UnderHentaiProvider.kt` — Class skeleton + properties
**File:** `UnderHentaiProvider/src/main/kotlin/com/example/underhentai/UnderHentaiProvider.kt` (NEW)
**Action:** Create the full provider file. Details below.

**Imports:**
```kotlin
package com.example.underhentai

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
```

**Class properties (follow TioHentaiProvider pattern):**
```kotlin
class UnderHentaiProvider : MainAPI() {

    override var mainUrl = "https://www.underhentai.net"
    override var name = "UnderHentai"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
```

**MainPage sections:**
```kotlin
    override val mainPage = mainPageOf(
        Pair("$mainUrl/page/", "Latest"),
        Pair("$mainUrl/releases/page/", "Releases"),
        Pair("$mainUrl/uncensored/page/", "Uncensored"),
        Pair("$mainUrl/top/page/", "Top"),
    )
```
NOTE: The pagination pattern on underhentai.net is `/page/N/`. The `request.data` will contain the base URL prefix up to `/page/`, and `page` number gets appended. For the homepage the base is `/page/`, for releases it's `/releases/page/`, etc. The implementer must verify this works and adjust if the site uses `?page=N` instead — test by fetching `https://www.underhentai.net/page/2/` first.

**Helper — toSearchResponse():**
```kotlin
    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val anchor = this.selectFirst("h2 a") ?: this.selectFirst("a[href]") ?: return null
        val title = anchor.text().trim()
        if (title.isEmpty()) return null
        val href = fixUrl(anchor.attr("href"))
        val poster = this.selectFirst("img")?.let { img ->
            img.attr("src").ifEmpty { img.attr("data-src") }
        }
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }
```
CSS logic rationale: Homepage articles use `h2 > a` for title+link and `img` for poster. The `data-src` fallback handles WP Rocket lazy loading. This selector works for homepage, /index/, /releases/, /uncensored/, /top/, and /tag/ pages since they all use the same `.content article` card layout.

**getMainPage():**
```kotlin
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page/"
        val doc = app.get(url).document

        val home = doc.select(".content article").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home
            ),
            hasNext = doc.select("a[href*='/page/']").any {
                it.text().contains("Next", ignoreCase = true) || it.text().contains("»")
            }
        )
    }
```
Pagination detection: Look for a "Next »" link. The site shows `1 2 3 ... 70 Next »` pagination. If no "Next" link exists, there are no more pages.

**search():**
```kotlin
    override suspend fun search(query: String): List<SearchResponse> {
        // WordPress standard search: /?s=query
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select(".content article").mapNotNull { it.toSearchResponse() }
    }
```
IMPORTANT: If `/?s=query` doesn't work (returns homepage or empty), the implementer must try these alternatives in order:
1. `/?s=$query` (WordPress default) — most likely
2. `/search/$query/` (pretty permalink variant)
3. Check if the search form has a different `action` attribute by inspecting `doc.selectFirst("form.search input[name]")` to find the actual query parameter name.

**load():**
```kotlin
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text()
            ?: doc.selectFirst(".article-header h2")?.text()
            ?: ""
        val poster = doc.selectFirst(".content img[src*='static.underhentai.net']")?.attr("src")
            ?: doc.selectFirst(".content img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        val genres = doc.select("a[href*='/tag/']").map { it.text().trim() }

        val episodes = ArrayList<Episode>()

        // Extract watch links: /watch/?id=X&ep=Y
        doc.select("a[href*='/watch/?']").forEachIndexed { index, a ->
            val href = fixUrl(a.attr("href"))
            // Try to extract episode number from context
            // Episodes are grouped under "Episode NN" headers
            // Each episode may have multiple language variants (Raw, English sub, Spanish sub)
            // We want unique episodes, not duplicates per language
            val epParam = Regex("""ep=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()

            // Avoid duplicates: only add if this ep param hasn't been seen
            if (epParam != null && episodes.none { it.data == href }) {
                episodes.add(
                    newEpisode(href) {
                        this.name = a.text().trim().ifEmpty { "Episode ${index + 1}" }
                        this.episode = epParam
                    }
                )
            }
        }

        // If multiple variants per episode (Raw, English, Spanish subs), 
        // group by episode number. Each episode detail page shows all servers.
        // ALTERNATIVE approach if above produces too many duplicates:
        // Extract unique episode numbers from "Episode NN" headers instead,
        // then construct watch URLs for just one variant per episode (e.g., ep=0 for first variant).

        return newAnimeLoadResponse(title, url, TvType.NSFW) {
            posterUrl = poster?.let { fixUrl(it) }
            addEpisodes(DubStatus.Subbed, episodes.sortedBy { it.episode })
            plot = description
            tags = genres
        }
    }
```

CRITICAL NOTE on episode extraction: The detail page for underhentai.net lists episodes with MULTIPLE variants per episode (Raw, English sub, Spanish sub). Each variant has a different `ep=N` value. For example, "Cafe Junkie" Episode 01 has ep=0 (Raw), ep=1 (English), ep=2 (Spanish). Episode 02 has ep=3, ep=4, ep=5.

**Decision made:** Include ALL variants as separate episodes so the user can choose their preferred language. Label them accordingly by reading the surrounding context (🎌 Raw, 💬 Subbed + language flag).

Refined approach for episode extraction:
```kotlin
        // Each episode block structure on the detail page:
        // "Episode 01" header
        //   🎌 Raw Censored → /watch/?id=X&ep=0
        //   💬 Subbed (English) → /watch/?id=X&ep=1
        //   💬 Subbed (Spanish) → /watch/?id=X&ep=2
        // "Episode 02" header
        //   🎌 Raw → /watch/?id=X&ep=3
        //   etc.
        
        // Strategy: find all watch links, extract ep number, 
        // read nearby text for language info
        doc.select("a[href*='/watch/?']").forEach { a ->
            val href = fixUrl(a.attr("href"))
            val epParam = Regex("""ep=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            
            // Try to find language context from parent/sibling elements
            val parent = a.closest("tr, div, li") ?: a.parent()
            val contextText = parent?.text() ?: ""
            val langLabel = when {
                contextText.contains("Spanish", ignoreCase = true) || contextText.contains("🇪🇸") -> "ES Sub"
                contextText.contains("English", ignoreCase = true) || contextText.contains("🇺🇸") -> "EN Sub"
                contextText.contains("Raw", ignoreCase = true) || contextText.contains("🎌") -> "Raw"
                else -> ""
            }
            
            if (episodes.none { it.data == href }) {
                episodes.add(
                    newEpisode(href) {
                        this.name = if (langLabel.isNotEmpty()) "Ep $epParam ($langLabel)" else "Episode $epParam"
                        this.episode = epParam
                    }
                )
            }
        }
```

**loadLinks():**
```kotlin
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // The watch page has server tabs that load iframes
        // Look for iframe sources in the page (may be in script tags or direct iframes)
        
        // 1. Direct iframes
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty() && !src.startsWith("about:")) {
                loadExtractor(fixUrl(src), mainUrl, subtitleCallback, callback)
            }
        }

        // 2. Iframes in scripts (lazy-loaded via JS)
        doc.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("iframe") || scriptData.contains("src=")) {
                val iframeRegex = Regex("""(?:src|url)\s*[=:]\s*['"]([^'"]+)['"]""")
                iframeRegex.findAll(scriptData).forEach { match ->
                    val url = match.groupValues[1]
                    if (url.startsWith("http") && !url.contains("underhentai.net")) {
                        loadExtractor(url, mainUrl, subtitleCallback, callback)
                    }
                }
            }
        }

        // 3. Look for direct video URLs (mp4, m3u8)
        doc.select("script").forEach { script ->
            val scriptData = script.data()
            val videoRegex = Regex("""(https?://[^'"]+\.(?:mp4|m3u8)[^'"]*)""")
            videoRegex.findAll(scriptData).forEach { match ->
                val videoUrl = match.groupValues[1]
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        return true
    }
}
```

**QA for Provider.kt:**
1. Verify `mainUrl` resolves correctly with `fixUrl()`
2. Test `getMainPage()` returns non-empty list for page 1
3. Test `search()` with a known title (e.g., "cafe junkie")
4. Test `load()` on a detail page returns title + episodes
5. Test `loadLinks()` returns at least one ExtractorLink from a watch page
6. Verify no crashes on empty pages or missing elements (null-safe selectors)

### Task 4: Update `settings.gradle.kts`
**File:** `settings.gradle.kts` (EDIT)
**Action:** Add `"UnderHentaiProvider"` to the include list.
**Before:**
```kotlin
include(
    "TioHentaiProvider",
    "HentailaProvider",
    "HentaijkProvider"
)
```
**After:**
```kotlin
include(
    "TioHentaiProvider",
    "HentailaProvider",
    "HentaijkProvider",
    "UnderHentaiProvider"
)
```
**QA:** Verify the trailing comma is correct and no syntax errors.

### Task 5: Verify build compiles
**Action:** Run `./gradlew UnderHentaiProvider:build` (or equivalent on Windows: `gradlew.bat UnderHentaiProvider:build`)
**Expected:** Build succeeds with no errors.
**If build fails:** Check namespace derivation — the root `build.gradle.kts` computes `namespace = "com.example.${project.name.lowercase().replace("provider", "")}"` which for `UnderHentaiProvider` yields `com.example.underhentai`. Ensure the package declaration matches.

## Final Verification Wave
**DO NOT mark work as complete until the user explicitly confirms.**

1. All 4 files created/modified
2. Build compiles without errors
3. Provider appears in CloudStream3 when extension is loaded
4. `getMainPage()` returns content for at least one section
5. `search()` returns results for a known title
6. `load()` returns title, poster, and episodes for a detail page
7. `loadLinks()` extracts at least one video stream from a watch page

## Known Risks & Mitigations
1. **Search endpoint unknown**: Assumed WordPress `/?s=query`. If it fails, try alternatives listed in Task 3.
2. **Watch page iframes are JS-rendered**: The `app.get()` only gets static HTML. If iframes are injected purely via JavaScript (not in HTML source), `loadLinks()` will return empty. Mitigation: inspect the raw HTML response of `/watch/?id=X&ep=Y` — if iframes are in `<script>` tags as strings, the regex approach works. If fully client-side rendered, may need to hit an API endpoint instead.
3. **Episode variant deduplication**: Multiple sub languages per episode produce many entries. The plan includes all variants labeled by language — user can adjust if they prefer only one language.
4. **WP Rocket lazy loading**: Images may use `data-src` instead of `src`. The `toSearchResponse()` helper handles both.
5. **Rate limiting/Cloudflare**: If the site has protection, requests may fail. Standard CS3 approach is to add `interceptor` if needed — out of scope for initial implementation.
