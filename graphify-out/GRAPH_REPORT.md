# Graph Report - .  (2026-04-23)

## Corpus Check
- Corpus is ~2,449 words - fits in a single context window. You may not need a graph.

## Summary
- 41 nodes · 30 edges · 11 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Hentaijk Provider Core|Hentaijk Provider Core]]
- [[_COMMUNITY_TioHentai Provider Core|TioHentai Provider Core]]
- [[_COMMUNITY_Hentaila Provider Core|Hentaila Provider Core]]
- [[_COMMUNITY_Android & Cloudstream Config|Android & Cloudstream Config]]
- [[_COMMUNITY_Hentaijk Plugin Loader|Hentaijk Plugin Loader]]
- [[_COMMUNITY_Hentaila Plugin Loader|Hentaila Plugin Loader]]
- [[_COMMUNITY_TioHentai Plugin Loader|TioHentai Plugin Loader]]
- [[_COMMUNITY_Gradle Settings|Gradle Settings]]
- [[_COMMUNITY_Hentaijk Build Config|Hentaijk Build Config]]
- [[_COMMUNITY_Hentaila Build Config|Hentaila Build Config]]
- [[_COMMUNITY_TioHentai Build Config|TioHentai Build Config]]

## God Nodes (most connected - your core abstractions)
1. `HentaijkProvider` - 9 edges
2. `HentailaProvider` - 6 edges
3. `TioHentaiProvider` - 6 edges
4. `HentaijkPlugin` - 2 edges
5. `HentailaPlugin` - 2 edges
6. `TioHentaiPlugin` - 2 edges
7. `SearchObject` - 1 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 0 - "Hentaijk Provider Core"
Cohesion: 0.2
Nodes (1): HentaijkProvider

### Community 1 - "TioHentai Provider Core"
Cohesion: 0.25
Nodes (2): SearchObject, TioHentaiProvider

### Community 2 - "Hentaila Provider Core"
Cohesion: 0.29
Nodes (1): HentailaProvider

### Community 3 - "Android & Cloudstream Config"
Cohesion: 0.67
Nodes (0): 

### Community 4 - "Hentaijk Plugin Loader"
Cohesion: 0.67
Nodes (1): HentaijkPlugin

### Community 5 - "Hentaila Plugin Loader"
Cohesion: 0.67
Nodes (1): HentailaPlugin

### Community 6 - "TioHentai Plugin Loader"
Cohesion: 0.67
Nodes (1): TioHentaiPlugin

### Community 7 - "Gradle Settings"
Cohesion: 1.0
Nodes (0): 

### Community 8 - "Hentaijk Build Config"
Cohesion: 1.0
Nodes (0): 

### Community 9 - "Hentaila Build Config"
Cohesion: 1.0
Nodes (0): 

### Community 10 - "TioHentai Build Config"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **1 isolated node(s):** `SearchObject`
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Gradle Settings`** (1 nodes): `settings.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Hentaijk Build Config`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Hentaila Build Config`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `TioHentai Build Config`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What connects `SearchObject` to the rest of the system?**
  _1 weakly-connected nodes found - possible documentation gaps or missing edges._