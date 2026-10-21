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
## Open Decisions
Assumption delegated by the request to implement autonomously: Android 8+ baseline, phone-first responsive layout, no account or internet permission.
