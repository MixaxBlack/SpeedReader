# SpeedReader
<!-- impeccable:product-schema 1 -->
## Platform
android
## Stack
Native Java Android Views, Material components. Reuses AEROPORT's proven SDK 35 / Java 17 / Gradle 8.11.1 build configuration in an independent repository.
## Product Purpose
Read FB2 books one word at a time with adjustable speed, typography, colors, ten themes, pause, seeking and persistent reading positions.
## Users
People reading their own FB2 books on Android. English and Russian UI explicitly requested.
## Capabilities and Constraints
Offline import using Android document picker, internal book copies, per-book progress. AEROPORT must remain untouched. APK must be built, installed and tested before publication.
Reading speed is specified in words per minute, from 30 to 500 inclusive, in whole-word increments.
User-requested folder scanning builds the library from FB2 file metadata. Scan selected Android document trees recursively, remember granted folders for repeat scans, deduplicate by book content and keep offline copies. Library displays title, author, series, genre, language and word count where available; search by title, author or series. Original files are read only.
Scan phone searches accessible shared internal/SD/USB storage read-only, with optional all-files access requested only by that action on API 30+ (legacy read-storage permission on older Android). The original document-picker and folder flows remain usable without broad access. Private app directories and Android/data/obb are excluded. Found files persist title/filename, author and source path, and display the filename separately when it differs from the title; results open cached books or retry the original. Separate file paths remain visible while offline books deduplicate by content. Scans retain completed imports on cancellation and are bounded to 100,000 entries / 10,000 FB2 candidates.
Completed scan summaries are formatted in the current interface language when shown.
Manual screen rotation toggles portrait/landscape independently of system auto-rotate, pauses reading and saves position without changing the reading-font preference. Orientation is not stored as a preference.
## Open Decisions
Assumption delegated by the request to implement autonomously: Android 8+ baseline, phone-first responsive layout, no account or internet permission.
