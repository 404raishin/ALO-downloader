#!/usr/bin/env bash
# Installs the minified release APK on a running emulator, shares two links to it the way
# a user would, and checks that the files arrive in Download/ALO. Prints the app's log so
# failures in the release build (R8, native libraries, Python) are visible in CI.
set -u
APK=$(ls app/build/outputs/apk/release/*x86_64*.apk | head -1)
PKG=com.alo.downloader
BASE=${FIXTURE_BASE:?set FIXTURE_BASE}

adb install -r -g "$APK" || exit 1
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 5

share() {
  adb shell am start -W -a android.intent.action.SEND -t text/plain \
    --es android.intent.extra.TEXT "'$1'" -n $PKG/.share.ShareActivity >/dev/null
}

wait_for() { # folder pattern
  for _ in $(seq 1 90); do
    if adb shell ls "/sdcard/Download/ALO/$1" 2>/dev/null | grep -q "$2"; then return 0; fi
    sleep 2
  done
  return 1
}

status=0
share "$BASE/alo-test-clip.mp4"
if wait_for Other '\.mp4'; then echo "OK yt-dlp video"; else echo "FAIL yt-dlp video"; status=1; fi
share "$BASE/alo-test-image.png"
if wait_for Other '\.png'; then echo "OK gallery-dl image"; else echo "FAIL gallery-dl image"; status=1; fi

echo "---- Download/ALO ----"
adb shell ls -laR /sdcard/Download/ALO 2>/dev/null
echo "---- app log ----"
APP_PID=$(adb shell pidof $PKG | tr -d '\r')
adb logcat -d > release-logcat.txt
grep -E "AndroidRuntime|FATAL|YtDlp|GalleryDl|Python|chaquopy|DownloadExecutor|DownloadQueueWorker|WM-|Exception|Error" release-logcat.txt \
  | grep -E "$PKG|${APP_PID:-xxxx}|AndroidRuntime|FATAL" | tail -150
exit $status
