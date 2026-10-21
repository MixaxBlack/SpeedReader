Native offline Android FB2 speed reader. Android 8.0 and newer.

- English / Русский / system interface language.
- One-word reading, pause, seek, back/forward 10 words; automatic per-book position saving.
- 30–500 words/min, 18–72 sp text, four system font families.
- Ten themes and custom foreground/background colors.
- FB2 UTF-8 / Windows-1251; ZIP with FB2 as the first file. 32 MB limit.
- Internal offline library; no account and no internet permission.

Signed release APK. CI builds and lints, runs parser and reader instrumentation tests on Android 15, installs and launches the actual release APK, verifies its signature and publishes SHA256SUMS.

Download SpeedReader-1.4.1.apk below and allow installation from your browser when Android asks.

Full-width word area. Tap the speed value to access the vertical speed slider in Appearance; +1/−1 stay on the reader. Long words temporarily shrink to fit, then the next word uses the preferred size again.

New library scanner: Library → Find FB2. Choose one or more book folders through Android, scan subfolders, and repeat scans without duplicates. Read title, author, series, genre and language from FB2 files. Search the library, see word counts/progress, stop scanning and keep already imported books. Malformed/inaccessible files are reported and skipped. Book copies remain available offline. Original files are read only; this folder flow needs no all-files permission.

Refined native design: warm paper, Manrope with Cyrillic, serif book headings, restrained flat buttons, readable search, consistent borders and two-column theme previews. Instant word changes and speed adjustments, native press/focus feedback.

Scan phone searches accessible shared internal storage and available SD/USB volumes. This separate action requests optional all-files access in Android Settings on API 30+, or legacy read-storage permission on older Android. The document picker and folder scan remain usable without broad access. Private app directories and Android/data/obb are excluded. Found files persist title/filename, author and path, support search and tap-to-read from the offline cache or original file, and mark failed imports for retry. Separate file paths remain visible while cached books deduplicate by content. Stop scan retains completed imports. Compact screens place folder actions and Found files / Books under More.

Manual screen rotation: use the outlined rotation icon beside Open FB2 / Library to switch portrait and landscape with system auto-rotate off. Reading pauses and position is saved; font size stays unchanged and orientation is not persisted. A pending phone scan also starts after an Android Settings return with access granted, including when Android restarted the app during that grant.

Refinements: found-file rows explicitly show the filename when it differs from the book title, with a single-line path using middle ellipsis. Completed scan summaries follow the current interface language. Reading-speed endpoint labels are centered. Compact reader scrolling keeps system-bar inset protection fixed outside the scrolling content.
