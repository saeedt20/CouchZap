#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-results
capture() {
  local label="$1"
  timeout 10s adb logcat -d > "startup-results/$label-logcat.txt" || true
  timeout 10s adb logcat -d -b crash > "startup-results/$label-crash.txt" || true
  timeout 10s adb shell dumpsys activity activities > "startup-results/$label-activities.txt" || true
  cat "startup-results/$label-crash.txt"
}
trap 'capture interrupted' EXIT

timeout 60s adb install startup-results/baseline.apk
adb logcat -c
timeout 15s adb shell am start -n com.couchzap.iptv/.MainActivity || true
sleep 5
capture baseline
timeout 5s adb shell pidof com.couchzap.iptv || true
timeout 15s adb uninstall com.couchzap.iptv

timeout 60s adb install startup-results/candidate.apk
for attempt in 1 2 3; do
  adb logcat -c
  timeout 15s adb shell am start -n com.couchzap.iptv/.MainActivity
  sleep 5
  capture "candidate-$attempt"
  timeout 5s adb shell pidof com.couchzap.iptv
  timeout 15s adb shell uiautomator dump /sdcard/startup.xml
  adb pull /sdcard/startup.xml "startup-results/candidate-$attempt-ui.xml"
  python3 -c "from pathlib import Path; assert 'Import playlist' in Path('startup-results/candidate-$attempt-ui.xml').read_text()"
  timeout 15s adb shell am force-stop com.couchzap.iptv
 done
trap - EXIT
