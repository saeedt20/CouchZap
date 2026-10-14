#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-results
capture() {
  timeout 10s adb logcat -d > startup-results/logcat.txt || true
  timeout 10s adb logcat -d -b crash > startup-results/crash.txt || true
  cat startup-results/crash.txt
}
trap capture EXIT
dump() {
  timeout 15s adb shell uiautomator dump /sdcard/check.xml
  timeout 10s adb pull /sdcard/check.xml "startup-results/$1-ui.xml"
}
click() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys,xml.etree.ElementTree as E,re,subprocess
root=E.parse('startup-results/'+sys.argv[1]+'-ui.xml')
node=next(n for n in root.iter('node') if n.attrib.get(sys.argv[2])==sys.argv[3])
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
assert x2>x1 and y2>y1
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True,timeout=10)
PY
}
timeout 60s adb install startup-results/candidate.apk
timeout 15s adb shell am start -n com.couchzap.iptv/.MainActivity
sleep 3
dump language
click language text 'English'
sleep 1
dump welcome
click welcome resource-id com.couchzap.iptv:id/btn_public
sleep 1
dump providers
click providers text 'iptv-org'
sleep 1
dump public
click public text 'All public channels'
loaded=false
for attempt in {1..12}; do
  sleep 3
  dump channels
  if python3 - <<'PY'
import xml.etree.ElementTree as E
assert any(n.attrib.get('resource-id')=='com.couchzap.iptv:id/et_channel_search' for n in E.parse('startup-results/channels-ui.xml').iter('node'))
PY
  then loaded=true; break; fi
 done
test "$loaded" = true
timeout 10s adb shell input keyevent 82
sleep 1
dump settings
click settings resource-id com.couchzap.iptv:id/btn_settings_crt_row
sleep 1
dump sound-enabled
python3 - <<'CHECK_SOUND'
import xml.etree.ElementTree as E
assert any(n.attrib.get('resource-id')=='com.couchzap.iptv:id/btn_settings_crt_row' and n.attrib.get('checked')=='true' for n in E.parse('startup-results/sound-enabled-ui.xml').iter('node'))
CHECK_SOUND
click sound-enabled resource-id com.couchzap.iptv:id/btn_settings_crt_row
for attempt in {1..8}; do
  dump settings
  if click settings resource-id com.couchzap.iptv:id/btn_settings_logs; then break; fi
  timeout 10s adb shell input swipe 180 600 180 180 400
 done
sleep 1
dump report
python3 - <<'PY'
import re,xml.etree.ElementTree as E
texts=[n.attrib.get('text','') for n in E.parse('startup-results/report-ui.xml').iter('node')]
assert any('CouchZap diagnostic report' in t and re.search(r'Playlist kind: public; channels: [1-9][0-9]*',t) for t in texts)
PY
timeout 10s adb shell input keyevent 4
for attempt in {1..8}; do
  dump settings
  if click settings resource-id com.couchzap.iptv:id/btn_settings_update; then break; fi
  timeout 10s adb shell input swipe 180 250 180 600 400
 done
sleep 4
dump update
python3 - <<'PY'
import xml.etree.ElementTree as E
assert any('2.1.2 is up to date' in n.attrib.get('text','') for n in E.parse('startup-results/update-ui.xml').iter('node'))
PY

timeout 10s adb shell pidof com.couchzap.iptv
