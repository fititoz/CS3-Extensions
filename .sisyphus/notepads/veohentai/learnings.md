Created VeoHentaiProvider with standard Cloudstream 3 extension structure. Implemented scraping logic for home, search, detail, and watch pages based on the provided CSS selectors.
Fixed VeoHentaiProvider.kt:
- Updated load() to treat the URL as an episode directly, as the site is episode-first.
- Updated loadLinks() to search for all iframes and check for data-src to handle lazy loading.
- Verified that getMainPage and search use the correct selectors for the 'a' container.
