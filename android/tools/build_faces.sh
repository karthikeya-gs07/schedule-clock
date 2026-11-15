#!/bin/sh
# Writes every colourway's face (RULES §9), one per product flavour of :watchface.
# Run from android/: sh tools/build_faces.sh
set -e
# Each face carries the Focus layer (RULES A26); its colours also go into the phone preview's table.
rm -f ../prototype/focus-themes.js
for c in amber mint violet rose solaris quartz copper ocean aurora acid lunar desert lime plum forest arctic orange jaeger prime platinum electric glacier ember cobalt; do
  python3 tools/gen_spike_face.py "watchface/src/$c/res" "$c" ../prototype/focus-themes.js
done
python3 tools/gen_spike_face.py watchface/src/red/res red   # red mode (RULES A25)
