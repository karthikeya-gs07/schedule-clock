#!/bin/sh
# Writes the five colourway faces (RULES §9), one per product flavour of :watchface.
# Run from android/: sh tools/build_faces.sh
set -e
for c in amber mint violet rose solaris; do
  python3 tools/gen_spike_face.py "watchface/src/$c/res" "$c"
done
