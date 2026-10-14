#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-results
capture() {
  timeout 10s adb logcat -d > startup-results/logcat.txt || true
  timeout 10s adb logcat -d -b crash > startup-results/crash.txt || true
  cat startup-results/crash.txt
}
trap capture EXIT
timeout 60s adb install startup-results/candidate.apk
for attempt in 1 2 3; do
  adb logcat -c
  timeout 15s adb shell am start -n com.couchzap.iptv/.MainActivity
  sleep 5
  timeout 5s adb shell pidof com.couchzap.iptv
  timeout 15s adb shell uiautomator dump /sdcard/startup.xml
  adb pull /sdcard/startup.xml "startup-results/startup-$attempt-ui.xml"
  python3 -c "from pathlib import Path; assert 'Import playlist' in Path('startup-results/startup-$attempt-ui.xml').read_text()"
  timeout 10s adb shell input keyevent 82
  sleep 1
  timeout 15s adb shell uiautomator dump /sdcard/settings.xml
  adb pull /sdcard/settings.xml "startup-results/settings-$attempt-ui.xml"
  python3 -c "from pathlib import Path; assert 'com.couchzap.iptv:id/btn_settings_close' in Path('startup-results/settings-$attempt-ui.xml').read_text()"
  timeout 10s adb shell input keyevent 4
  timeout 15s adb shell am force-stop com.couchzap.iptv
 done
