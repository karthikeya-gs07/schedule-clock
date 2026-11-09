# Phone screens as editable SVGs

`fold-cover-1080x2520/`: the five phone app pages (Today, Watchface, Calendars, Settings, About) at the Galaxy Z Fold cover screen size, 1080 × 2520 px, in demo mode (sample day, no real calendar).

Each file has named layers: background and colourway glow, status bar, buttons, switches, dividers, navigation, live text in **Google Sans Flex** (install `android/phone/src/main/res/font/google_sans_flex.ttf` to edit it in Figma or Illustrator), and icons drawn from the app's own paths (`Ui.kt` Icons). The watch and its face (and About's face close-ups) are embedded images.

Made by `make_svg.py` from an emulator capture (screenshot + UI dump) at 1080 × 2520, 420 dpi:

```
python3 make_svg.py <capture folder> <output folder> android/phone/src/main/res/font/google_sans_flex.ttf
```
