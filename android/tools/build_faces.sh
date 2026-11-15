#!/bin/sh
# Writes every colourway's face (RULES §9), one per product flavour of :watchface.
# Run from android/: sh tools/build_faces.sh
set -e
for c in amber mint violet rose solaris quartz copper ocean aurora acid lunar desert lime plum forest arctic orange jaeger prime platinum electric glacier ember cobalt red; do
  python3 tools/gen_spike_face.py "watchface/src/$c/res" "$c"
done
# Focus mode (RULES A26): a Focus face in each colourway's colours, and those colours for the phone's preview.
rm -f ../prototype/focus-themes.js
for c in amber mint violet rose solaris quartz copper ocean aurora acid lunar desert lime plum forest arctic orange jaeger prime platinum electric glacier ember cobalt; do
  C=$(printf %s "$c" | cut -c1 | tr a-z A-Z)$(printf %s "$c" | cut -c2-)
  python3 tools/gen_focus_face.py "watchface/src/focus$C/res" "$c" ../prototype/focus-themes.js
done
