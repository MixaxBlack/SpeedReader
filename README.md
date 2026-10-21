# SpeedReader

Native offline Android FB2 speed reader. English and Russian interface.

[Download APK](https://github.com/MixaxBlack/SpeedReader/releases/latest/download/SpeedReader-1.4.1.apk) · [Build and test](https://github.com/MixaxBlack/SpeedReader/actions/workflows/android.yml)

Open an `.fb2` book with the system document picker. Tap Read or the word to start/pause; adjust speed (30–500 words/min), drag the position slider or step ten words. Appearance offers 18–72 sp, four fonts, ten themes, custom colors and English / Русский / system language. Large words shrink to fit the stage. Books are copied to private app storage; each book has its own saved word position. Reading pauses when the app leaves the foreground. Reading resumes paused after restart. The rotation button beside Open FB2 / Library switches portrait and landscape even with system auto-rotate off. Rotation pauses reading and saves the position, keeping the font-size setting; orientation is not saved as a preference. No network access. Scan phone optionally requests broad shared-storage access; opening individual books and choosing folders remain available without it.

Supports namespace-aware FB2, inline markup, UTF-8 and Windows-1251. Named supplementary bodies (notes) are excluded. DTDs and empty or malformed documents are rejected. FB2 input is bounded to 32 MB and one million words. ZIP support requires the first file to be FB2. Uninstalling clears books and progress.

## Build

Infrastructure versions reproduced from AEROPORT's Android pipeline: Java 17, Gradle 8.11.1, Android SDK platform 35 / build-tools 35.0.0, Android Gradle Plugin 8.9.2. The SDK is provisioned by `android-actions/setup-android` in GitHub Actions; no AEROPORT sources, signing files or hosting are shared or changed. This workstation has no SDK/JDK on PATH; CI supplies the existing proven Android toolchain.

```sh
gradle :app:assembleDebug :app:lintRelease :app:assembleDebugAndroidTest
gradle :app:connectedDebugAndroidTest
```

For a release set SPEEDREADER_KEYSTORE to a private keystore containing the `speedreader` alias, and SPEEDREADER_STORE_PASSWORD / SPEEDREADER_KEY_PASSWORD. The workflow initializes its own signing identity and preserves it in a repository-scoped Actions cache. Preserve that cache for update compatibility; move the key to a backed-up signing secret before long-term distribution. Never commit a signing key. The APK is a release build, not debuggable.

CI gates release publication on lint, instrumentation tests (encoding, XML boundaries, ZIP, local cache, playback, seeking, persistence and languages), installation/launch of the actual signed APK and signature verification. Screenshots and test reports are attached to the run as `android-verification`. A checksum accompanies the release.

The reading stage uses the full width. Tap the underlined speed value to open the vertical 30–500 words/min slider in Appearance; +1/−1 remain on the reader. Speed supports touch, keyboard and accessibility progress actions. Each word is measured at the preferred font size and shrinks only when needed; the next word restores that preferred size automatically without changing the saved setting.

## Scan books into the library

Open Library → Find FB2, choose a folder (for example Books or Documents) and allow read access. SpeedReader scans that folder and all its subfolders for `.fb2` and `.fb2.zip`, imports valid books, skips damaged files and merges content duplicates. Add multiple folders, then use Scan again to revisit all remembered folders. Android restricts selecting storage roots, the Download root and app-private directories; select a permitted book folder inside shared storage. This folder flow does not require all-files access. On screens below 500dp height, choose More → Choose folder or Scan folders again.

The library reads embedded title, author names, series/number, genres and language from `description/title-info`, plus word count and saved progress. Search matches title, author, series, genre or language. Missing author data is labeled as missing; a missing title falls back to the source filename in the library. Metadata sidecars keep browsing lightweight. Existing cached books are migrated automatically without changing their content IDs or positions.

Scans run off the UI thread and report added/duplicate/skipped/unavailable counts. Stop scan keeps already imported books. Leaving the library, rotating or closing the app stops an active scan; use Scan again to continue without duplicates. Each scan is bounded to 100,000 documents / 10,000 FB2 candidates; choose smaller folders when that limit is reached. Imported copies remain readable if source access is later revoked. Uninstalling clears this private library and folder permissions. Originals are never modified.
## Scan phone and browse found files

Library → Scan phone searches accessible shared internal storage and available SD/USB volumes for `.fb2` and `.fb2.zip`. On Android 11/API 30+, this action opens Android Settings for optional all-files access; on older Android it requests read-storage permission. If Android restarts the app while Settings is open, a pending phone scan starts on return once access is granted. Denying access still allows the document picker and folder scanning. Private app storage remains excluded; the scanner also skips Android/data and Android/obb. Originals are read only.

Found files lists embedded title (or filename), author where present and source path. Tap a row to open its cached offline book, or retry reading the original when no cache is available. Search matches title, author or path, including filename. Unimportable files are marked for retry. The found-file list is saved privately in phone-files.json and restored later; separate source paths remain listed even when book content is deduplicated in the offline library. A new scan refreshes the list. Stop scan keeps completed imports and available results.

The scanner checks canonical paths to prevent cycles and traversal outside discovered storage roots, and stops at 100,000 entries or 10,000 FB2 candidates. Protected or unavailable locations are reported. On taller screens, Scan phone and Found files / Books appear above folder actions; below 500dp height, Scan phone and More provide the same actions through a compact menu.
