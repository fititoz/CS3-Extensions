---
type: "query"
date: "2026-04-24T00:10:41.245859+00:00"
question: "What connects SearchObject to the rest of the system?"
contributor: "graphify"
source_nodes: ["SearchObject", "TioHentaiProvider"]
---

# Q: What connects SearchObject to the rest of the system?

## Answer

SearchObject is a node defined in TioHentaiProvider.kt (L20). It is structurally linked to the TioHentaiProvider community, which includes the main provider class and its core methods like .search() and .toSearchResponse(). It likely serves as a data transfer object for search results within that specific provider.

## Source Nodes

- SearchObject
- TioHentaiProvider