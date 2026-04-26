# Learnings

- Updated root `build.gradle.kts` to use a dynamic namespace for subprojects.
- Pattern used: `namespace = "com.example.${project.name.lowercase().replace("provider", "")}"`
- This ensures each subproject (e.g., TioHentaiProvider, HentailaProvider) has a unique namespace derived from its name.
