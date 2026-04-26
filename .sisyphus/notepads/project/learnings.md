## Category Change to Anime
- Changed TvType.NSFW to TvType.Anime in all providers to improve visibility in CloudStream 3.
- Updated build.gradle.kts files to match the new category.
- Updated README.md to clarify that content is still for adults despite the 'Anime' category.
Updated author metadata to 'fititoz' and removed explicit language settings across all extensions to align with GitHub profile and remove redundancy.
Restored Spanish language ('es') for Hentaila, HentaiJK, and TioHentai.
Set English language ('en') for UnderHentai.
Language configuration in Cloudstream extensions involves both the Provider class (lang property) and the build.gradle.kts file (language property).
Updated UnderHentai language to es-MX to support both Spanish and English content while appearing for Spanish users.
## UnderHentai Multi-Language Configuration
- Configured UnderHentai as a multi-language extension by setting 'lang' and 'language' properties to 'multi'.
- This indicates support for both English (EN) and Spanish (ES) in CloudStream 3.
## HentaiJK Provider Fix
- Updated iframe extraction to handle escaped quotes in JS source.
- Added support for jkplayer/umv and player.setup in Sabrosio extractor.
- Handled relative URLs in iframes using fixUrl.
Updated HentaiJK episode extraction to use total episode count from metadata when AJAX-loaded episodes are not present in initial HTML.

- HentaiJK: Use 'div.anime_info h3' instead of 'h3' to extract the series title to avoid picking up the search history heading.
## LatinoHentaiProvider Fix
- CloudStream 3's AnimeLoadResponse expects addEpisodes() or a List, not a Map for the episodes field.
- Correct usage: addEpisodes(DubStatus.Subbed, episodes)
## Kotlin Compilation in CloudStream-3 Extensions
- 
ewAnimeSearchResponse takes exactly 2 positional arguments: (name: String, url: String).
- 
ewHomePageResponse should be called with named arguments or a HomePageList object.
- Suspend functions like pp.get, pp.post, and loadExtractor CANNOT be called inside orEach lambdas on Elements or List. Use standard or loops instead.
- Episode objects should be created using the 
ewEpisode helper function for better compatibility.
## Kotlin Suspend Functions in Loops
In Kotlin, suspend functions cannot be called from within a forEach lambda. Use a standard for (item in list) loop instead.

## CloudStream3 DSL Signatures
- newHomePageResponse expects a HomePageList object as its first argument.
- newAnimeSearchResponse takes title and url as primary arguments.
- load method in MainAPI should return a non-nullable LoadResponse.
- Use newEpisode(url) { ... } instead of the Episode constructor directly.
