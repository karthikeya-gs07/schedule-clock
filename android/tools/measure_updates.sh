#!/bin/bash
# Spike measurement: memory (PSS) of the data source and the WFF runtime, and CPU time spent
# across N tap-driven updates (deep link toggling the 1 o'clock meeting).
# Usage: tools/measure_updates.sh <label> [N]
set -euo pipefail
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
LABEL="$1"; N="${2:-20}"
DS=io.github.karthikeyags07.scheduleclock
RT=com.google.wear.watchface.runtime
TICK=$("$ADB" shell getconf CLK_TCK | tr -d '\r')

pss() { "$ADB" shell dumpsys meminfo "$1" | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}'; }
cpu() { local pid; pid=$("$ADB" shell pidof "$1" | tr -d '\r'); "$ADB" shell cat "/proc/$pid/stat" | awk '{print $14 + $15}'; }

"$ADB" shell am start -W -a android.intent.action.VIEW -d "app://$DS/select?hour=7" >/dev/null  # warm up, select nothing
sleep 2
ds0=$(cpu $DS); rt0=$(cpu $RT)
for i in $(seq 1 "$N"); do
  "$ADB" shell am start -a android.intent.action.VIEW -d "app://$DS/select?hour=1" >/dev/null
  sleep 1.2
done
sleep 2
ds1=$(cpu $DS); rt1=$(cpu $RT)
echo "$LABEL: data source PSS $(pss $DS) KB, runtime PSS $(pss $RT) KB"
echo "$LABEL: CPU per update  data source $(( (ds1 - ds0) * 1000 / TICK / N )) ms, runtime $(( (rt1 - rt0) * 1000 / TICK / N )) ms  (N=$N, includes the face redraw)"
