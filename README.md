# SpeedReader

Native offline Android FB2 speed reader. English and Russian interface.

[Download APK](https://github.com/MixaxBlack/SpeedReader/releases/latest/download/SpeedReader-1.0.0.apk) · [Build and test](https://github.com/MixaxBlack/SpeedReader/actions/workflows/android.yml)

Open an `.fb2` book with the system document picker. Tap Read or the word to start/pause; adjust speed (60–1200 words/min), drag the position slider or step ten words. Appearance offers 18–72 sp, four fonts, ten themes, custom colors and English / Русский / system language. Large words shrink to fit the stage. Books are copied to private app storage; each book has its own saved word position. Reading pauses when the app leaves the foreground. Reading resumes paused after restart. No network access or broad storage permission.

Supports namespace-aware FB2, inline markup, UTF-8 and Windows-1251. Named supplementary bodies (notes) are excluded. DTDs and empty or malformed documents are rejected. FB2 input is bounded to 32 MB and one million words. ZIP support requires the first file to be FB2. Uninstalling clears books and progress.

## Build

Infrastructure versions reproduced from AEROPORT's Android pipeline: Java 17, Gradle 8.11.1, Android SDK platform 35 / build-tools 35.0.0, Android Gradle Plugin 8.9.2. The SDK is provisioned by `android-actions/setup-android` in GitHub Actions; no AEROPORT sources, signing files or hosting are shared or changed. This workstation has no SDK/JDK on PATH; CI supplies the existing proven Android toolchain.

```sh
gradle :app:assembleDebug :app:lintRelease :app:assembleDebugAndroidTest
gradle :app:connectedDebugAndroidTest
```

For a release set SPEEDREADER_KEYSTORE to a private keystore containing the `speedreader` alias, and SPEEDREADER_STORE_PASSWORD / SPEEDREADER_KEY_PASSWORD. The workflow initializes its own signing identity and preserves it in a repository-scoped Actions cache. Preserve that cache for update compatibility; move the key to a backed-up signing secret before long-term distribution. Never commit a signing key. The APK is a release build, not debuggable.

CI gates release publication on lint, instrumentation tests (encoding, XML boundaries, ZIP, local cache, playback, seeking, persistence and languages), installation/launch of the actual signed APK and signature verification. Screenshots and test reports are attached to the run as `android-verification`. A checksum accompanies the release.
