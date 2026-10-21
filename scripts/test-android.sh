#!/usr/bin/env bash
set -euo pipefail
mkdir -p verification
pull_screenshots() {
  mkdir -p verification/screenshots
  for name in phone-en.png phone-ru-night.png phone-ru-large-font.png settings-ru.png settings-themes-ru.png library-en.png library-ru.png library-landscape.png; do
    adb pull "/data/local/tmp/speedreader-$name" "verification/screenshots/$name" || true
  done
}
trap pull_screenshots EXIT
gradle --no-daemon :app:connectedDebugAndroidTest
pull_screenshots
# Install the actual release APK after removing the differently signed test build.
if adb shell pm list packages com.mixax.speedreader | grep -q 'package:com.mixax.speedreader'; then
  adb uninstall com.mixax.speedreader
fi
adb install app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n com.mixax.speedreader/.MainActivity | tee verification/release-start.txt
sleep 3
adb shell pidof com.mixax.speedreader
adb logcat -d -s AndroidRuntime:E > verification/release-errors.txt
if grep -q 'FATAL EXCEPTION' verification/release-errors.txt; then cat verification/release-errors.txt; exit 1; fi
adb exec-out screencap -p > verification/release-installed.png
# Large system font and narrow landscape are retained as review evidence.
adb shell settings put system font_scale 1.3
sleep 2
adb exec-out screencap -p > verification/large-font.png
adb shell settings put system font_scale 1.0
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 2
adb exec-out screencap -p > verification/landscape.png
adb shell pidof com.mixax.speedreader
adb shell settings put system user_rotation 0
adb shell cmd uimode night yes
sleep 2
adb exec-out screencap -p > verification/system-dark-mode.png
adb shell pidof com.mixax.speedreader
adb shell cmd uimode night no
