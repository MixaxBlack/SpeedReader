#!/usr/bin/env bash
set -euo pipefail
mkdir -p verification
pull_screenshots() {
  mkdir -p verification/screenshots
  for name in phone-en.png phone-ru-night.png phone-ru-large-font.png speed-settings.png long-word.png restored-word.png reader-rotated.png reader-rotated-controls.png phone-access.png phone-found-en.png phone-found-ru.png settings-ru.png settings-themes-ru.png library-en.png library-ru.png library-landscape.png; do
    adb pull "/data/local/tmp/speedreader-$name" "verification/screenshots/$name" || true
  done
}
trap pull_screenshots EXIT
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.mixax.speedreader.PhoneStorageGrantTest,com.mixax.speedreader.PhoneStorageOfflineTest
# Android restarts the UID when storage access changes. Change app-ops between
# instrumentation processes, as a real return from Android Settings would do.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set --uid com.mixax.speedreader MANAGE_EXTERNAL_STORAGE allow
adb shell am instrument -w -r -e class com.mixax.speedreader.PhoneStorageGrantTest com.mixax.speedreader.test/androidx.test.runner.AndroidJUnitRunner | tee verification/phone-granted-tests.txt
grep -q 'OK (1 test)' verification/phone-granted-tests.txt
adb shell appops set --uid com.mixax.speedreader MANAGE_EXTERNAL_STORAGE ignore
adb shell am instrument -w -r -e class com.mixax.speedreader.PhoneStorageOfflineTest com.mixax.speedreader.test/androidx.test.runner.AndroidJUnitRunner | tee verification/phone-offline-tests.txt
grep -q 'OK (1 test)' verification/phone-offline-tests.txt
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
