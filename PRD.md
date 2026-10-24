# Schedule Clock: Product Requirements (living doc)

> Status: **v0.18 — design prototype complete enough for a Wear OS spike. No watch code yet.**
> Last updated: 2026-10-24
>
> **Documents**
> - **This file:** what we're building, for whom, on what platform, open questions, risks, tooling, history.
> - **[RULES.md](RULES.md):** the source of truth for *behavior and design*: what the face does in every situation, and why. Rule IDs (W1, R4, C6…) are referenced below.
> - **[prototype/index.html](prototype/index.html):** interactive HTML prototype that implements RULES.md (true-size watch tabs, time slider, sample days, ambient, slow motion, fast-forward, live complication payload).

---

## 1. Concept

A round watch face that shows the time **and the shape of your day**. Calendar meetings are drawn as arcs on the edge of the dial at the hours they occupy, and the next meeting's details sit under the clock. Tapping an hour on the ring brings that meeting forward; the clock shrinks and the details grow.

## 2. Goals

- Glanceable: time and the next meeting readable in under 2 seconds.
- Free vs. busy time visible on the dial itself, including what's later today, double bookings, and tomorrow morning when it's within the next 12 hours.
- Details of any meeting in the next 12 hours without opening an app.
- Ship as a real, publishable Wear OS watch face.

## 3. Experience summary

Full detail in [RULES.md](RULES.md). In short:

| Area | Summary | Rules |
|---|---|---|
| Time window | Main ring = next 12 h. Meetings that don't fit yet (rest of today, or tomorrow but starting in the window) wait on a thin inner lane, then animate onto the ring once their slot is free. Tomorrow's meetings within 14 h also wait on the lane (toggle). Nothing further out. | W1–W10, M3 |
| Ring | Amber arcs; elapsed part dimmed; double bookings as split coral stripes; private meetings as a grey caution-stripe band; later lane thin blue; now dot with comet trail; 15-min ticks; numbers 1–12. | R1–R10 |
| Center | Date, big clock, then title / time / place of the next meeting. Qualifiers: a time-of-day icon (sunrise / sun / sunset / moon) before the time of later-lane meetings, "Tomorrow ·", overlap icon + clashing meeting (double booking). During a live meeting the text looks ahead to the next meeting, marked by an amber **→** before its title. Private meetings show only 🔒 **Private** · time · "Details hidden". With no meetings: just date + clock, centered, no message. | C1–C13 |
| Type | 24 px minimum everywhere; ellipsis instead of shrinking. | T1–T4 |
| Taps | 12 one-hour tap sections on the ring; repeated taps cycle through meetings in that hour (near first, then the later lane); a precise tap on the thin lane jumps straight to the later meeting; auto-return after 8 s. Center tap on a private meeting opens the calendar app. | I1–I11 |
| Motion | One layout, two sizes: clock shrinks, details grow (360 ms). Text dips only when the shown meeting changes. Lane → ring settle (700 ms). Reduced motion respected. | M1–M6 |
| Ambient | Grey outlines, no trail, title only, no taps. | A1–A3 |

### Worked example (default sample day)

| Time | What the face shows |
|---|---|
| 6:00 AM | Amber 7–7:45 AM (Gym) with thin blue 7–8 PM (Client call) stacked inside it on the lane; grey-striped 10–11 AM (Doctor appointment, private), amber 1–2 PM split coral (Design review + Vendor call double-booked), amber 4–5 PM. Details: Design review · 1:00 – 2:00 PM · **⧉ Vendor call** (overlap icon in coral). |
| Tap 10 o'clock | 🔒 **Private** · 10:00 – 11:00 AM · Details hidden. Tap the center → calendar app opens. |
| Tap 7 o'clock | Gym selected. Tap again → Client call: blue lane arc thickens; details: **🌇 7:00 – 8:00 PM** (sunset icon) · Zoom. Tap again → default. |
| Tap the thin lane at 7 | Client call directly (skips Gym). |
| 8:00 AM | Client call settles from the blue lane onto the amber ring at 7→8. |
| 4:20 PM | Weekly sync (4–5) is live: its arc shows elapsed/remaining. Text looks ahead: **→ Client call** · 7:00 – 8:00 PM · Zoom. Tapping 4 o'clock shows Weekly sync (no arrow). |
| 9:00 PM | Today is done; tomorrow's 8:00 AM breakfast is within 12 h → amber arc at 8→8:45; details: **Tomorrow · Café Blue**. 10 AM board prep isn't shown yet. |
| 10:30 PM | Board prep (10–11 AM tomorrow) starts within the window but ends past it → thin blue lane. |
| Nothing scheduled | Just the date and a larger, centered clock. |

## 4. Open questions

| # | Question | Status |
|---|---|---|
| Q1 | Platform | **Decided:** Wear OS / Galaxy Watch |
| Q2 | Which calendar(s)? Google, Outlook, iCloud, all on-phone calendars? | **Open.** Affects data access and permissions. |
| Q3 | 12 h dial vs. full day | **Decided:** main ring + later lane (W1–W8) |
| Q4 | Overlapping events | **Decided:** split coral stripes (R4, C7) |
| Q5 | All-day events | **Open.** Ignored for now (W9); maybe a small indicator. |
| Q6 | Past events | **Decided:** hidden (W7) |
| Q7 | Ambient mode | **Decided:** A1–A3 |
| Q8 | Exiting selection | **Decided:** I4, I5, I7 |
| Q9 | During a meeting | **Decided:** elapsed shown on the ring (R3, W2) |
| Q10 | Event colors | **Open.** Single amber for now; calendar colors later? |
| Q11 | Tap model | **Decided:** one-hour sections (I1–I7) |
| Q12 | Status line ("NEXT · IN 1H 30M") | Hidden for now (C10). Bring back? |
| Q14 | Private meetings: show the time, or hide everything? | **Decided (2026-10-24):** keep showing the time (C13). |
| Q13 | Tomorrow's meetings in the window: should they look different from today's on the main ring (beyond the "Tomorrow ·" text)? | **Open.** |

## 5. Non-goals (for now)

- Creating or editing events from the watch.
- RSVP / join-call actions *(possible later)*.
- Showing anything beyond the 12-hour window.

## 6. Platform & architecture: Wear OS

### 6.1 Hard constraints (verified 2026-10-24)

- **Watch Face Format (WFF) is mandatory.** Since January 2026, Wear OS only installs watch faces built in WFF (declarative XML, rendered by the system).
- **A WFF face contains no code.** Live data reaches it only through **complications**, with a **maximum of 8 slots**.
- **Taps in WFF can only** launch an app or deep link (`Launch`), cycle images, or trigger a complication's tap action.
- `BoundingArc` gives arc-shaped tap areas, but the bounding area also crops drawing.
- `Transform` + `Animation` ease a property whenever its value changes.

### 6.2 Architecture (two packages)

```
┌───────────────────────────┐   complication data (7 slots)   ┌──────────────────────────┐
│ Schedule Data Source app  │ ──────────────────────────────▶ │ Schedule Clock face (WFF)│
│ (Kotlin, runs on watch)   │                                 │ (XML only, no code)      │
│ • reads calendar on watch │                                 │ • ring, lane, clash arcs │
│ • applies RULES.md W/C    │ ◀────────────────────────────── │ • clock, date, details   │
│ • tracks selection        │   12 Launch deep links (taps)   │ • Transforms keyed on    │
└───────────────────────────┘                                 │   `selected`             │
                                                              └──────────────────────────┘
```

- **Data source app** computes lanes, overlaps, the next meeting and text qualifiers (RULES §1, §3), and pushes them as complication data. It uses complication **timelines** to move the elapsed edge and lane changes forward without waking up constantly.
- **Face** draws everything from those slots (slot plan: RULES §8) and animates on the `selected` value.
- **Taps:** 12 invisible `PartDraw` hotspots with `<Launch target="app://scheduleclock/select?hour=N">`. An invisible activity in the data source app updates the selection and finishes immediately.
- **Fallbacks** if tap handling via `Launch` is too slow or flashes: (A) six 2-hour sections as complication slots with `BoundingArc`; (B) tap anywhere to cycle.

### 6.3 Risks to validate in the spike

| # | Risk | How we test |
|---|---|---|
| 1 | **`Launch` → invisible activity → redraw** is fast and flash-free (decides one-hour vs two-hour sections) | Emulator + real watch: tap-to-redraw time, flicker |
| 2 | `Animation` runs when a `Transform` value changes **because of complication data**. If not, M1 snaps instead of animating | Animate opacity from a RANGED_VALUE 0 → 1 |
| 3 | Elements outside a complication (the clock) can read `selected`. If not, draw the clock inside the status slot | Spike XML |
| 4 | Lane → ring animation (M3) needs a transient slot (`ring_promote`) | Spike XML |
| 5 | Double-booking mask: one WEIGHTED_ELEMENTS feed drawn as a black mask (fixed color, data weights) plus two coral stripes | Spike XML |
| 6 | Comet trail (`SweepGradient` arc following the clock) is smooth and cheap on battery | Emulator + watch |
| 7 | `Launch` works on a transparent `PartDraw`; hotspots don't clash with complication taps | Spike XML |
| 8 | Transparent segment colors render as gaps in `WeightedStroke` | Emulator |
| 9 | Max weighted elements (believed ~20) is enough for a busy day | API limit + test |
| 10 | Non-customizable slots can default to our own data source | Spike + docs |
| 11 | Watch calendar sync covers **tomorrow morning** (`WearableCalendarContract` syncs ~24 h ahead; Galaxy Watch behavior to confirm) | Real Galaxy Watch |
| 12 | Two Play listings (face + data source) vs one bundle | Play publishing docs |
| 15 | **Private stripes in WFF:** `WeightedStroke` draws solid segments only. Options: a `Stroke` with dash intervals (perpendicular bars rather than 45°), a pre-rendered striped ring image masked by the `ring_private` arc, or encoding stripes as many tiny weighted elements (may exceed the element limit). This uses the **last free slot** (8 of 8) | Spike XML |
| 17 | **Precise lane taps (I11):** the lane tap zone is ~8 px (≈ 4 dp), far below the usual 48 dp touch target. Check it's hittable at all on a real watch, that touch slop doesn't misroute taps, and how WFF resolves overlapping `Launch` hotspots (lane vs band). If unreliable, drop I11; I3 still covers it | Real watch |
| 16 | Calendar "private" flag is available on the watch (`Events.ACCESS_LEVEL` / visibility via the watch calendar provider) | Real Galaxy Watch |
| 14 | ~~Font license~~ **Resolved:** the clock now uses Anta (SIL Open Font License), which may be bundled in a published app | — |
| 13 | Edge-flush ring on real hardware: some round screens are slightly cropped by the case or bezel; check that the outer few px of the band are visible on Galaxy Watch and Pixel Watch | Real watch + emulator |

### 6.4 Minimum OS

Wear OS 5+ (WFF v2, needed for `WeightedStroke` on `Arc`): Galaxy Watch 4 and later on current software, and Pixel Watch 1 and later.

## 7. Tooling requirements

| Tool | Needed for | Status |
|---|---|---|
| **Android Studio** (latest stable) | Building both packages; includes the SDK, `adb`, Gradle, JDK | ❌ Not installed |
| **Wear OS emulator image** (Wear OS 5 and 6, round) | Testing without a watch | ❌ Via Android Studio's SDK Manager |
| **WFF validator + memory footprint tool** (Google's `watchface` tools) | Play requires faces to pass these | ❌ I'll fetch once Android Studio is in |
| **Real Galaxy Watch** (Wear OS 5+), Developer options + ADB over Wi-Fi | Calendar sync, tap latency, battery | ❓ Do you have one? |
| **Google Play Console account** ($25 one-time) | Publishing | ❓ Later |
| **Python 3** | Serving the prototype locally (`python3 -m http.server`) | ✅ Built into macOS |
| **Figma** | Visual design, if we move there | ✅ Installed; Figma MCP needs authorizing via `/mcp` |
| Samsung **Watch Face Studio** | Optional WYSIWYG WFF editor | Optional |
| **Anta** font (Google Fonts, OFL) | Clock digits and dial numbers (RULES T5). Prototype loads it from Google Fonts; the WFF build bundles `Anta-Regular.ttf` | ✅ Prototype. TTF to download into the watch project when we start it |

### 7.1 Repository & preview

| Item | Status |
|---|---|
| **GitHub repo** [`karthikeya-gs07/schedule-clock`](https://github.com/karthikeya-gs07/schedule-clock) | ✅ **Public** (made public 2026-10-24 so free GitHub Pages works), `main` branch. Open it in Claude Code on the web (claude.ai/code) to work from a phone or iPad |
| **Live preview** | ✅ **https://karthikeya-gs07.github.io/schedule-clock/** via GitHub Pages from the root of `main`; every push to `main` redeploys (~1–2 min) |
| Pages-ready layout | ✅ Root `index.html` redirects to `prototype/`; `.nojekyll` disables Jekyll processing |

## 8. Changelog

- **v0.19:** Prototype shows the face at true physical size: tabs for Galaxy Watch8/Watch9 40 mm and 44 mm, Watch8 Classic 46 mm and Watch Ultra 2 47 mm (display diameter + case outline), plus a "Large" tab that fills the screen. Scale is auto-detected on iPhone/iPad, estimated elsewhere, and can be calibrated against a bank card.
- **v0.20:** Double-booking line drops the word "Also": a coral overlap icon (two intersecting rings) precedes the other meeting's name (C7, C13).
- **v0.21:** "Tomorrow look-ahead 14 h" toggle (default on): tomorrow's meetings start showing on the later lane 14 h ahead, so tomorrow 8 AM appears from 6 PM; further out stays hidden. Off = 12 h window only (W6).
- **v0.22:** On a double booking the clash line (icon + other meeting) sits directly under the title, with the time range below it (C7).
- **v0.23:** Smoother motion: S-curve easing and longer durations (520 ms select, 170/280 ms text swap, 900 ms lane settle with no bounce); arcs fade in/out and recolor instead of snapping (M1–M4b).
- **v0.24:** Later-lane meetings show a time-of-day icon (sunrise, sun, sunset, moon) before the time instead of the word "Later" (C5).
- **v0.18.2:** Repo made public; GitHub Pages live at https://karthikeya-gs07.github.io/schedule-clock/.
- **v0.18.1:** Project pushed to the private GitHub repo `schedule-clock`; live preview pending a hosting choice (§7.1).
- **v0.18:** Morning + evening meetings in the same dial slot stack (ring + lane). Tap order: morning, then evening; a precise tap on the thin lane selects the evening meeting directly. Sample day gains Gym 7–7:45 AM.
- **v0.17.2:** Private stripes are grey instead of amber.
- **v0.17.1:** Q14 closed: private meetings keep showing their time.
- **v0.17:** Private meetings: caution-stripe arcs, 🔒 "Private" + "Details hidden" (title and place never sent to the face), center tap opens the calendar app. All 8 complication slots now used.
- **v0.16:** NEXT/NOW label removed; an amber arrow before the title marks the upcoming meeting during a live one.
- **v0.15:** During a live meeting the details show the next upcoming meeting, with a NEXT indicator in the divider's slot (NOW when a live meeting is shown).
- **v0.14:** Clock digits and dial numbers switched to Anta (Google Fonts, open license) at 88 px; empty state enlarges the clock again; Monument Extended removed (license risk resolved).
- **v0.13:** Clock digits in Monument Extended (70 px), time + AM/PM centered as a unit; empty state no longer enlarges the clock. Font license flagged (risk 14).
- **v0.12:** Ring flush with the screen edge (R1); selected arc grows inward (R9).
- **v0.11:** Empty state is just date + clock (no message). Tomorrow's meetings inside the 12 h window are shown (main ring or lane); nothing beyond. Selection follows the meeting itself, not its list position. **RULES.md** introduced as the behavior source of truth; PRD restructured around it.
- **v0.10:** Later-lane meetings move to the main ring only once they fit completely, with an animation (e.g. the 7–8 PM call at 8:00 AM).
- **v0.9:** Later-today lane (thin, blue) and double-booking split coral stripes; "Also: …" line.
- **v0.8:** 24 px minimum text; time and place on separate lines; layout rebalanced.
- **v0.7:** 15-min ticks; centered-clock empty state; status line hidden; measured text fitting.
- **v0.6:** Comet trail; hour numbers 1–12.
- **v0.5:** Motion simplified to "one layout, two sizes".
- **v0.4:** Animated select/release; elapsed part of a live meeting on the ring.
- **v0.3:** One-hour tap sections via `Launch` deep links.
- **v0.2:** Tap-to-cycle; first interactive prototype.
- **v0.1:** Platform = Wear OS; WFF constraints; two-package architecture.
- **v0:** Concept captured.

(All on 2026-10-24.)

## 9. References

- [Watch Face Format overview](https://developer.android.com/training/wearables/wff)
- [WFF complications](https://developer.android.com/training/wearables/wff/complications)
- [Complication types & fields](https://developer.android.com/reference/wear-os/wff/complication/complication)
- [WeightedStroke](https://developer.android.com/reference/wear-os/wff/group/part/draw/style/weighted-stroke)
- [Launch](https://developer.android.com/reference/wear-os/wff/common/launch)
- [Bounding (BoundingArc)](https://developer.android.com/reference/wear-os/wff/complication/bounding)
- [ComplicationSlot (max 8)](https://developer.android.com/reference/wear-os/wff/complication/complication-slot)
- [Animation](https://developer.android.com/reference/wear-os/wff/common/transform/animation)
- [Transform](https://developer.android.com/reference/wear-os/wff/common/transform/transform)
- [What's new in watch faces (May 2025)](https://android-developers.googleblog.com/2025/05/whats-new-in-watch-faces.html)
