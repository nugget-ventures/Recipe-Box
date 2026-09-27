# RecipeBox Android MVP

Android-first native recipe library built with Kotlin + Jetpack Compose.

## What works in this MVP

- Native Android share target: share a text/web link to **RecipeBox**
- Secure-import flow using a separate HTTPS extractor service
- Local, offline recipe library
- Search across title, description, ingredients, categories, source and notes
- Categories/tags
- Portion scaling with common fractions
- Recipe photos from imported HTTPS image URLs
- Original source link + original video tutorial link
- Create and edit your own recipes
- Favorites
- Nicely formatted Android text sharing
- English + Unicode Chinese content (简体中文 / 繁體中文)
- Manual cloud backup/restore through Android's Storage Access Framework. Choose **Google Drive** in the system file picker to store the backup without giving RecipeBox broad Drive access.
- No WebView and no execution of imported webpage JavaScript

## Security guardrails

Incoming links accept only `http://` or `https://`. The Android app itself never loads imported recipe pages into a WebView. The included backend extractor:

- blocks non-web schemes and credential-bearing URLs
- resolves and rejects private, loopback, link-local, multicast, reserved and unspecified IPs
- rechecks every redirect
- limits redirects, response time and downloaded HTML size
- accepts HTML only
- does not execute JavaScript
- extracts structured recipe data / sanitized text
- keeps original video links as links rather than downloading arbitrary video files

For production, put the extractor in an isolated cloud service with outbound firewall rules blocking internal networks and cloud metadata addresses. That adds network-layer protection against DNS rebinding/SSRF beyond the application checks.

## Open in Android Studio

Use a current Android Studio that supports Android Gradle Plugin 9.4.x and install Android SDK 37. The project uses Compose BOM `2026.09.00`.

The build machine used to generate this source bundle does not contain the Android SDK, so an APK is not included in this bundle. Android Studio can download/sync the required dependencies.

## Connect the extractor

Deploy `/server` behind HTTPS, then edit `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "EXTRACTOR_BASE_URL", "\"https://your-extractor.example.com\"")
```

Recipe import intentionally refuses a non-HTTPS extractor.

## Google Drive backup

The MVP uses Android's system document picker. Tap **⋮ → Back up to cloud / Drive**, choose Google Drive, and save `RecipeBox-backup.json`. Restore uses the same system picker.

This design avoids shipping Google OAuth secrets and gives RecipeBox access only to the file the user explicitly chooses. Automatic background Drive backups can be added later with Google Identity + Drive appDataFolder after registering the production application/OAuth client.

## Current MVP limitations

- Automatic extraction is strongest for sites that publish Schema.org/JSON-LD Recipe metadata. Social platforms may expose only title/image/video URL unless a platform-specific integration is added.
- User-added recipes currently accept an HTTPS image URL rather than copying a local phone photo into the recipe archive.
- Traditional and Simplified Chinese both display/search normally when the exact characters match. Cross-script equivalence (e.g. `红烧肉` finding `紅燒肉`) is not yet implemented.
- Automatic scheduled Drive backup is not yet implemented; cloud backup is user-triggered via the secure system file picker.

## Cloud APK build with GitHub Actions

This project includes `.github/workflows/build-apk.yml`.

1. Create a GitHub repository and upload/push the contents of this `RecipeBoxAndroid` folder.
2. Open the repository's **Actions** tab.
3. Select **Build Android APK**.
4. Choose **Run workflow**.
5. When the run finishes, download `RecipeBox-debug.apk` from the run summary.

The workflow also builds automatically after pushes to `main` or `master`.

The resulting file is a debug APK intended for testing/sideloading, not a Play Store release. A production release should use a private signing key stored as repository secrets and build a signed release APK or Android App Bundle (AAB).
