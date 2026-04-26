# Plan: 4 New Hentai Extensions

## Objective
Create four new CloudStream 3 extension providers for the requested Spanish hentai websites:
1. HentailaTvProvider (https://hentaila.tv/)
2. VeoHentaiProvider (https://veohentai.com/)
3. EsHentaiTvProvider (https://eshentai.tv/)
4. LatinoHentaiProvider (https://latinohentai.com/)

## Project Requirements
All extensions must follow the established repository conventions:
- Language: lang = "es"
- Category: TvType.Anime
- Authors: authors = listOf("fititoz")
- Gradle: Standard layout with dynamic namespace matching the module name.
- Includes: Must be added to settings.gradle.kts.

---

## Tasks

### Task 1: Create HentailaTvProvider
File paths:
- HentailaTvProvider/build.gradle.kts
- HentailaTvProvider/src/main/kotlin/com/example/hentailatv/HentailaTvPlugin.kt
- HentailaTvProvider/src/main/kotlin/com/example/hentailatv/HentailaTvProvider.kt

Scraping Logic:
- Home/Search: Container .item_card, URL a.card__cover, Title .card__title, Image a.card__cover img
- Detail: Title h1, Desc .vraven_text.single p, Episodes .hentai__episodes ul li.hentai__chapter a
- Watch: Video is in .player_logic_item iframe (or extract from script regex).

### Task 2: Create VeoHentaiProvider
File paths:
- VeoHentaiProvider/build.gradle.kts
- VeoHentaiProvider/src/main/kotlin/com/example/veohentai/VeoHentaiPlugin.kt
- VeoHentaiProvider/src/main/kotlin/com/example/veohentai/VeoHentaiProvider.kt

Scraping Logic:
- Home/Search: Container #posts-home > a or .grid > a
- Title: h2
- Image: figure img
- Detail/Episodes: Frequently the search result goes to the series page where episodes are under .grid > a.
- Watch: Title h1, Desc .entry-content p, Iframe .aspect-w-16.aspect-h-9 iframe.

### Task 3: Create EsHentaiTvProvider
File paths:
- EsHentaiTvProvider/build.gradle.kts
- EsHentaiTvProvider/src/main/kotlin/com/example/eshentaitv/EsHentaiTvPlugin.kt
- EsHentaiTvProvider/src/main/kotlin/com/example/eshentaitv/EsHentaiTvProvider.kt

Scraping Logic:
- Home/Search: Container article.serie, Title h3.title, URL a, Image figure img.
- Detail: Title h1, Desc p.description, Episodes ul.h-caps li a.
- Watch: Video loads via JS into div#play-1. loadLinks must extract the base64 or raw URLs from script blocks or button onclick attributes.

### Task 4: Create LatinoHentaiProvider
File paths:
- LatinoHentaiProvider/build.gradle.kts
- LatinoHentaiProvider/src/main/kotlin/com/example/latinohentai/LatinoHentaiPlugin.kt
- LatinoHentaiProvider/src/main/kotlin/com/example/latinohentai/LatinoHentaiProvider.kt

Scraping Logic:
- Home/Search: Container article, Title h3, URL a, Image img.
- Detail: Title h1, Episodes under ul (header: Temporadas y episodios) li a.
- Watch: Dooplay/Toroplay style. Check for admin-ajax.php DOOPLAY requests for video links, or regex script tags for base64 embeds.

### Task 5: Project Integration & Testing
1. Update settings.gradle.kts: Add "HentailaTvProvider", "VeoHentaiProvider", "EsHentaiTvProvider", "LatinoHentaiProvider".
2. Update README.md to list the 4 new extensions.
3. Commit everything as atomic commits and push to GitHub.

## Final Verification Wave
1. Are all 4 Provider directories created correctly?
2. Do all 4 use TvType.Anime and lang = "es"?
3. Does settings.gradle.kts contain exactly 8 modules total?
4. Are all changes committed and pushed to main?
