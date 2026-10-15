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
release_version=$("$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging startup-results/candidate.apk | python3 -c "import re,sys; print(re.search(r\"versionName='([^']+)'\", sys.stdin.read()).group(1))")
export COUCHZAP_TEST_VERSION="$release_version"
timeout 60s adb install startup-results/candidate.apk
timeout 15s adb shell am start -n com.couchzap.iptv/.MainActivity
sleep 3
dump startup
if python3 - <<'CHECK_WELCOME'
import xml.etree.ElementTree as E
assert any(n.attrib.get('resource-id') == 'com.couchzap.iptv:id/btn_import' for n in E.parse('startup-results/startup-ui.xml').iter('node'))
CHECK_WELCOME
then
  dump welcome
  click welcome resource-id com.couchzap.iptv:id/btn_import_language
  sleep 1
  dump language
  python3 - <<'CHECK_LANGUAGES'
import xml.etree.ElementTree as E
labels = [n.attrib.get('text', '').removeprefix('✓  ') for n in E.parse('startup-results/language-ui.xml').iter('node') if n.attrib.get('class') == 'android.widget.Button']
expected = ['English', 'Español', 'हिन्दी', 'ไทย', 'فارسی', 'العربية']
assert [label for label in labels if label in expected] == expected, labels
CHECK_LANGUAGES
  click language text 'العربية'
  sleep 1
  dump arabic-welcome
  click arabic-welcome resource-id com.couchzap.iptv:id/btn_import_language
  sleep 1
  dump language
else
  cp startup-results/startup-ui.xml startup-results/language-ui.xml
fi
# The selected option may include the selection checkmark.
python3 - <<'CHOOSE_ENGLISH'
import re, subprocess, xml.etree.ElementTree as E
node = next(n for n in E.parse('startup-results/language-ui.xml').iter('node') if n.attrib.get('text', '').removeprefix('✓  ') == 'English')
x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
subprocess.run(['adb', 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)], check=True, timeout=10)
CHOOSE_ENGLISH
sleep 1
dump welcome
click welcome resource-id com.couchzap.iptv:id/btn_public
sleep 1
dump providers
click providers text 'iptv-org'
sleep 1
dump public
if python3 - <<'CHECK_VERSION'
import os
assert tuple(map(int, os.environ['COUCHZAP_TEST_VERSION'].split('.'))) >= (2, 2, 1)
CHECK_VERSION
then
  click public text 'Choose a country'
  catalog_ready=false
  for attempt in {1..15}; do
    sleep 1
    dump catalog
    if python3 - <<'CHECK_CATALOG'
import xml.etree.ElementTree as E
assert any(n.attrib.get('class') == 'android.widget.EditText' for n in E.parse('startup-results/catalog-ui.xml').iter('node'))
CHECK_CATALOG
    then catalog_ready=true; break; fi
  done
  test "$catalog_ready" = true
  printf '[]' > startup-results/catalog-focus.json
  for direction in down up; do
    if [ "$direction" = down ]; then count=35; key=20; else count=20; key=19; fi
    for ((step=0; step<count; step++)); do
      timeout 10s adb shell input keyevent "$key"
      dump catalog
      python3 - "$direction" <<'CHECK_ADJACENT'
import json, re, sys, xml.etree.ElementTree as E
from pathlib import Path
root = E.parse('startup-results/catalog-ui.xml')
page = next(n for n in root.iter('node') if n.attrib.get('resource-id') == 'com.couchzap.iptv:id/settings_pages')
rows = []
for n in page.iter('node'):
    bounds = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
    if n.attrib.get('class') == 'android.widget.Button' and len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]: rows.append(n)
focused = [n for n in rows if n.attrib.get('focused') == 'true']
assert len(focused) == 1, 'Country row must retain remote focus'
labels = [n.attrib['text'] for n in rows]
label = focused[0].attrib['text']
trace_file = Path('startup-results/catalog-focus.json')
trace = json.loads(trace_file.read_text())
if trace:
    previous = trace[-1]
    assert previous in labels, f'Focus jumped away from {previous} to {label}'
    assert labels.index(label) == labels.index(previous) + (1 if sys.argv[1] == 'down' else -1), (previous, label)
else:
    assert labels.index(label) == 0, 'First Down must reach the first country'
trace.append(label); trace_file.write_text(json.dumps(trace))
CHECK_ADJACENT
    done
  done
  timeout 10s adb shell input keyevent 4
  sleep 1
  dump public
fi
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
import os, xml.etree.ElementTree as E
assert any(os.environ['COUCHZAP_TEST_VERSION'] + ' is up to date' in n.attrib.get('text','') for n in E.parse('startup-results/update-ui.xml').iter('node'))
PY

timeout 10s adb shell pidof com.couchzap.iptv
