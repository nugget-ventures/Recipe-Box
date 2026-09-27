# MVP implementation status

## Implemented
- Android share target
- URL allow-list validation
- secure remote extraction contract
- secure extractor reference server
- offline local persistence
- library/search/categories/favorites
- portion scaling
- notes
- original source + video links
- formatted text sharing
- own-recipe editor
- Simplified/Traditional Chinese Unicode storage/display
- manual Google Drive/cloud backup through Android document picker

## Next production steps
1. Add instrumentation/unit tests and CI.
2. Deploy extractor to a locked-down HTTPS runtime with outbound network restrictions.
3. Add a local image picker and include copied user photos in backup archives.
4. Add OpenCC-style Simplified/Traditional cross-script search normalization.
5. Add automatic encrypted Drive appDataFolder backups after Google OAuth registration.
6. Improve platform-specific extraction for YouTube/Instagram/TikTok/Bilibili where permitted by their APIs/terms.
7. Add cook mode and cooking history.
8. Replace working package id `com.example.recipebox` and app name before Play Store release.
