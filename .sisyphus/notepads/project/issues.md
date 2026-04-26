## PowerShell Environment
- Standard 'grep' and 'GIT_MASTER=1 git' syntax fails in PowerShell. Use 'Select-String' and '\=1; git' instead.
## PowerShell Git Issues
- Using @{upstream} in PowerShell requires quotes or escaping curly braces.
- Setting environment variables in PowerShell uses \=VAL syntax.
## LatinoHentaiProvider.kt Compilation Errors
- Line 25: Invalid signature for 
ewHomePageResponse. Expected HomePageList or List<HomePageList>.
- Line 33: 
ewAnimeSearchResponse called with 3 arguments, but only 2 are accepted.
- Line 45: load return type should be LoadResponse (non-nullable) to match other providers.
- Line 56: Episode constructor used incorrectly. Should use 
ewEpisode.
- Line 94: pp.post (suspend) called inside orEach (non-suspend lambda).
- Line 108, 112: loadExtractor (suspend) called inside orEach.

## VeoHentaiProvider.kt Compilation Errors
- Line 116: loadExtractor (suspend) called inside orEach (non-suspend lambda) at line 119.
