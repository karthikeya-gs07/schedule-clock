# Schedule Clock

A Wear OS watch face that shows the time **and the shape of your day**: calendar meetings are drawn as arcs on the dial at the hours they occupy, with the next meeting's details under the clock.

**Status:** design prototype. No watch code yet.

## Files

| Path | What it is |
|---|---|
| [`PRD.md`](PRD.md) | Product requirements: concept, platform (Wear OS / Watch Face Format), open questions, risks, tooling, changelog |
| [`RULES.md`](RULES.md) | **Source of truth for behavior and design.** What the face does in every situation, and why (rule IDs W1, R4, C6, …) |
| [`prototype/index.html`](prototype/index.html) | Interactive HTML prototype implementing RULES.md: true-size tabs for Galaxy Watch 40 / 44 / Classic 46 / Ultra 47 mm (plus a full-screen "Large" view), time slider, sample days, ambient mode, slow motion, fast-forward, live complication payload |
| `index.html` | Redirects the GitHub Pages site to the prototype |
| [`android/`](android) | Wear OS build: `watchface` (Watch Face Format XML, no code) and `datasource` (Kotlin complication data source). Currently the Phase 1 spike (PRD §6.3a) |

## Live preview

**https://karthikeya-gs07.github.io/schedule-clock/**

Published with GitHub Pages from the root of `main`. Every push to `main` updates it, usually within a minute or two. The root `index.html` redirects to `prototype/`, and `.nojekyll` serves files as-is.

## Dial only

Press **Dial only** (or the `F` key) to hide every control and see just the watch. Add `#dial` to the URL (for example `…/schedule-clock/#dial`) to open straight into it; `Esc` or **Show controls** brings the panel back.

## Run locally

```bash
python3 -m http.server 8765 --directory prototype
```

Then open http://localhost:8765.

## Build the watch face (Mac with Android Studio)

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Install both APKs on a running Wear OS emulator or watch, then set the face:

```bash
adb install -r android/datasource/build/outputs/apk/debug/datasource-debug.apk && adb install -r android/watchface/build/outputs/apk/debug/watchface-debug.apk && adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --es watchFaceId io.github.karthikeyags07.scheduleclock.watchface
```

## Working on it from another device

Open this repository in Claude Code on the web (claude.ai/code) from your phone or iPad, make changes in a session, and push to `main`. The Pages preview updates automatically. Keep `RULES.md` and `PRD.md` updated with every design change.
