#!/usr/bin/env bash
# Installs the APK on the emulator and takes screenshots of the main screens.
# Never fails: if something does not work, it notes it in capturas/registro.txt and continues.
OUT=capturas
mkdir -p "$OUT"
LOG="$OUT/registro.txt"
LAST_XY=""
log() { echo "$*" | tee -a "$LOG"; }

shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png" && log "captura $1"; }

# Taps the first element whose text or description contains $1 (case-insensitive).
tap_text() {
  local xy="" try
  for try in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
    xy=$(python3 - "$1" <<'PY'
import re, sys
import xml.etree.ElementTree as ET
want = sys.argv[1].lower()
try:
    nodes = list(ET.parse("ui.xml").iter("node"))
except Exception:
    nodes = []
# First an identical text (a button); if there is none, one that contains it.
def label(n):
    return [(n.get("text") or "").strip().lower(), (n.get("content-desc") or "").strip().lower()]
exact = [n for n in nodes if want in label(n)]
partial = [n for n in nodes if any(want in l for l in label(n))]
for n in (exact or partial)[:1]:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
    [ -n "$xy" ] && break
    sleep 1
  done
  if [ -z "$xy" ]; then log "no encontré «$1»"; return 1; fi
  LAST_XY=$xy
  adb shell input tap $xy
  log "toqué «$1» en $xy"
}

swipe_up() { adb shell input swipe 540 1700 540 500 400; }
swipe_down() { adb shell input swipe 540 500 540 1900 300; }

adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee -a "$LOG"
adb shell pm grant app.oido android.permission.RECORD_AUDIO
adb shell pm grant app.oido android.permission.POST_NOTIFICATIONS
adb shell am start -W -n app.oido/.MainActivity >/dev/null
shot 1-inicio

wait_for() {
  for i in $(seq 1 ${2:-60}); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
    grep -q "$1" ui.xml && { log "apareció «$1» tras $i intentos"; return 0; }
    sleep 3
  done
  log "no apareció «$1»"
}

tap_text "Nueva frase"
sleep 2
adb shell input text "hola"
adb shell input keyevent 4   # closes the keyboard
shot 2-editor

# Spoken response (spec 001): type a text with current-time data fields.
tap_text "Una voz"
sleep 1
adb shell input text "Hoy%ses"
tap_text "+ día"
tap_text "+ fecha"
adb shell input keyevent 4
shot 2b-editor-voz
tap_text "Un sonido"
sleep 1

tap_text "Grabar"
shot 3-grabar
tap_text "● Grabar"
sleep 3
shot 4-grabando
# While recording the counter changes constantly and uiautomator may fail to read the screen:
# the same button is tapped by its position.
[ -n "$LAST_XY" ] && adb shell input tap $LAST_XY
sleep 1
tap_text "Usar"
sleep 3
shot 5-editor-con-grabacion

tap_text "Listo"
sleep 1
shot 6-lista

tap_text "Ajustes"
shot 7-ajustes
adb shell input keyevent 4
sleep 1

tap_text "Empezar a escuchar"
sleep 4
shot 8-preparando
wait_for "Dejar de escuchar" 80
sleep 2
shot 9-escuchando
tap_text "Dejar de escuchar"
sleep 2

tap_text "hola"
sleep 2
tap_text "Dictar con la voz"
sleep 6
shot 10-dictado
exit 0
