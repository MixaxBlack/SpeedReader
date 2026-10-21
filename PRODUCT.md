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
## Open Decisions
Assumption delegated by the request to implement autonomously: Android 8+ baseline, phone-first responsive layout, no account or internet permission.
