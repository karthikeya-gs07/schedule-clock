# Calen-Time: Product Requirements (living doc)

> Status: **v0.40 — Spikes 1–5 verified on the emulator (§6.3a–e): the face shows the prototype's center content, AM/PM and scrolling long titles with fake meetings. Next: read the real calendar, then real-watch checks.**
> Last updated: 2026-10-25
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
| Q5 | All-day events | **Decided (2026-10-26):** kept as events, never drawn themselves; every meeting that day is marked as clashing with them (W9). |
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

### 6.2 Architecture (three packages)

```
┌───────────────────────────┐  Data Layer item `/calendar`
│ Calen-Time phone app      │  (yesterday → end of tomorrow;
│ (Kotlin, Android phone)   │   private events without title/place)
│ • reads the phone calendar│ ─────────────────────────────┐
│ • syncs on change + 15 min│                              ▼
└───────────────────────────┘
┌───────────────────────────┐   complication data (7 slots)   ┌──────────────────────────┐
│ Calen-Time data source    │ ──────────────────────────────▶ │ Calen-Time face (WFF)    │
│ (Kotlin, runs on watch)   │                                 │ (XML only, no code)      │
│ • phone events + watch cal│                                 │ • ring, lane, clash arcs │
│ • applies RULES.md W/C    │ ◀────────────────────────────── │ • clock, date, details   │
│ • tracks selection        │   12 Launch deep links (taps)   │ • Transforms keyed on    │
└───────────────────────────┘                                 │   `selected`             │
                                                              └──────────────────────────┘
```

- **Data source app** computes lanes, overlaps, the next meeting and text qualifiers (RULES §1, §3), and pushes them as complication data. It uses complication **timelines** to move the elapsed edge and lane changes forward without waking up constantly.
- **Phone app** (added 2026-10-27, §6.3f): Galaxy watches keep calendar events private to Samsung Calendar, so the phone sends them. Same application id and signing key as the data source app (the Data Layer requires both). It syncs when opened, a few seconds after any calendar change (WorkManager content trigger) and every 15 minutes. The watch keeps the latest copy in a file, and also reads its own calendar when that's allowed (Pixel and other watches).
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
| 18 | **Calendar access on Galaxy watches:** events live in Samsung Calendar's private provider; the standard and Wear OS calendar providers are empty (§6.3f) | Real Galaxy Watch: ❌ confirmed 2026-10-27 → phone companion app (§6.2) |
| 14 | ~~Font license~~ **Resolved:** the clock now uses Anta (SIL Open Font License), which may be bundled in a published app | — |
| 13 | Edge-flush ring on real hardware: some round screens are slightly cropped by the case or bezel; check that the outer few px of the band are visible on Galaxy Watch and Pixel Watch | Real watch + emulator |

### 6.3a Spike 1 results (2026-10-25, Wear OS 6 emulator)

Code: `android/` (`watchface` = WFF XML generated by `android/tools/gen_spike_face.py`; `datasource` = Kotlin, fake meetings 1–2 PM and 4–5 PM).

| Risk | Result | Evidence / notes |
|---|---|---|
| **1. `Launch` tap → invisible activity → redraw** | ✅ **Pass** | Tap → deep link fired at +34 ms → `TapActivity` at +229 ms → face has new data at **+516 ms**. No flash: the face stays on screen (`Theme.NoDisplay`, finishes in `onCreate`). Keeps the one-hour tap sections (I1); fallbacks not needed |
| **2. Animation on complication-driven values** | ✅ **Pass** | `Transform` + `Animation` keyed on `RANGED_VALUE_VALUE` eases: clock height 34 → 50 → 62 → 63 px and the marker fades 255 → 122 → 6 → 0 over ~0.5 s (frame burst) |
| **3. Clock reacting to `selected`** | ✅ **Pass** | A `DigitalClock` inside the status slot's `Complication` renders and scales. So the clock, date and details all live inside the status slot |
| **7. `Launch` on a transparent `PartDraw`** | ✅ **Pass** | 72 × 72 transparent hotspots above the slots receive taps; full-screen slots with no tap action don't steal them |
| **8. Gaps in `WeightedStroke`** | ✅ **Pass, with a workaround** | Fully transparent `0x00000000` reaches the face as `#0` → runtime error "Unknown color" → whole ring drawn white. **Free time must use `#10000000`** (or any color with 8 hex digits) |
| **9. Max weighted elements** | ❌ **Fail** | `WeightedElementsComplicationData.getMaxElements()` = **7** on Wear OS 6. Each meeting needs a segment plus a gap, so one ring slot holds only **3 meetings**. Needs a new approach (see below) |
| **10. Our data source as the default** | ✅ **Pass** | `primaryProvider="package/package.Class"` works; the runtime binds `RingDataSourceService` and `StatusDataSourceService` with no user setup. **Each slot's `supportedTypes` must include `EMPTY`** (the fallback type), or the face fails to load |
| 4, 5, 6, 13, 15, 17 | ⏳ Not yet | Lane → ring, clash mask, trail, edge, private stripes, lane taps |
| Validator / memory footprint | ⏳ Blocked | Downloading and running Google's `wff-validator.jar` was refused by the session's permission check; needs your OK |

**Other gotchas found:** `WeightedElementsComplicationData` must carry a text, title or image (or the data source crashes); provider names use `package/package.Class`.

**Proposed fix for risk 9 (to test in spike 2):** have the data source draw the whole ring (meetings, lane, clash stripes, private stripes) into **one image** and send it as a full-face `PHOTO_IMAGE` complication. No element limit, the caution stripes come free (risk 15), and it frees 3 slots (`ring_later`, `ring_clash`, `ring_private`). The selected arc stays its own slot so it can still animate. To measure: image update speed, memory and battery.

### 6.3b Spike 2 results (2026-10-25): the ring as one image

The data source draws the whole ring (meetings, later lane, double-booking stripes, private caution stripes) into a transparent 450 × 450 PNG (`RingRenderer.kt`) and sends it as a full-face image complication. Test day: the prototype's busy day, 9 meetings incl. a full and a partial double booking, a private lunch and a later-lane call.

| Check | Result |
|---|---|
| **Risk 9: more than 3 meetings** | ✅ **Solved.** All 9 meetings, both clash ranges, private stripes and the lane render correctly |
| **Risk 15: private caution stripes** | ✅ **Solved** by drawing them in the image (45° stripes via a rotated bitmap shader) |
| **Risk 5: double-booking mask** | ✅ Moot: the gap and coral stripes are drawn in the image |
| Image type | ⚠️ `PHOTO_IMAGE` is **rejected** for our provider (WearServices: "Unexpected complication data type PHOTO_IMAGE"). **`SMALL_IMAGE` with `SmallImageType.PHOTO` works** at full-face size |
| **Speed** | ✅ Tap → face has the new image in **156–180 ms** warm (421 ms cold). Draw + PNG encode: **avg 30 ms, max 92 ms** (40 renders). Faster than spike 1 (516 ms) |
| **Size** | ✅ 25–27 KB PNG per update |
| **Memory** | ✅ Data source 31 MB, runtime 31 MB (spike 1: 29 / 30 MB): about +2 MB each |
| **CPU per update** (battery proxy) | ⚠️ Data source ~70 ms (spike 1: ~56), runtime ~100 ms (spike 1: ~79), warm. About +35 ms per update. Estimate: ~330 updates/day (every 5 min for the elapsed edge, plus taps) ≈ **~1 min of CPU per day**. Likely small, but **must be measured on a real watch** (emulator CPU ≠ watch, no real battery) |
| **Ambient** | ✅ The image still renders when the screen dozes (system dims it). The grey always-on look (A1) needs a second, grey image via the ambient image field |
| Selected arc | ⚠️ Drawn into the image, the selection gets hidden under the clash stripes (Design review at 1–2 PM). Keep the selected arc as its own slot on top (slot 2), as planned |
| Slot updates | ⚠️ Gotcha: after changing a slot's provider or type, the watch keeps the old binding. **Uninstall and reinstall the face** to re-apply the defaults |

**Decision:** adopt the ring image. Slots: `ring` (image: ring + lane + clash + private) replaces four weighted-element slots, freeing 3 for later (RULES §8).

**Still open:** lane → ring settle animation (risk 4, can't tween inside an image: use the transient slot or a face-side arc); comet trail (6); real-watch checks (edge 13, lane taps 17, battery, calendar sync 11, private flag 16); validator (blocked on permission).

### 6.3c Spike 3 results (2026-10-25): selected arc, lane → ring settle, comet trail

| Item | Result | How / evidence |
|---|---|---|
| **Selected arc on top (R9)** | ✅ **Pass** | Slot 2 is a `RANGED_VALUE`: MIN/MAX = the arc's start/end angle, VALUE = MAX when shown, MIN when hidden. The face animates stroke `thickness` (12 → 22 px, growing inward) and `alpha`; it's never hidden under clash stripes. ~~Angles animated too, so the arc slid between meetings~~ → **changed v0.35.3:** that read as a stray arc sweeping the face, so angles now jump and a switch fades out in place, then in at the new meeting |
| **Risk 4: lane → ring settle (M3)** | ✅ **Pass** | Slot 5 `RANGED_VALUE`, VALUE = progress lane → ring; the data source steps it: arc placed on the lane (+342 ms), moving (+662 ms), image takes over (+1342 ms). A `Group` scale (R − 10 → R) plus thickness 4 → 12 px and a blue/amber crossfade animate over 0.9 s (mid-frame captured). Polish: the crossfade passes through a lavender grey; WFF v4 can animate color directly |
| **Risk 6: comet trail (R6)** | ✅ **Pass** | 240 fixed dashes drawn by the face, each `alpha` computed from `[HOUR_0_11]`/`[MINUTE]` (`%`, `clamp`, `pow`). No data source involvement. Now dot = `Group` rotated by the same time expression. Cost: **+~0.3 s CPU per minute interactive** (1.69 → 1.98 s/min on the debug face with a seconds clock), **no measurable change in ambient** |
| Layering | ✅ | Track → trail → ring image → selected arc → settle arc → now dot → clock/details → hotspots. The ring image no longer draws the track, so free time is transparent and the trail shows through |

**Gotchas found:**
- ❗ `<Animation interpolation="CUBIC_BEZIER" controls="0.4, 0, 0.2, 1">` (comma-separated) **silently freezes the whole renderer** after one frame: no error in the log. **Use space-separated `controls="0.4 0 0.2 1"`.** Found by bisecting.
- The ring image re-renders immediately, so it highlights the destination before the sliding arc arrives. Fix in the real build: the image dims the others but doesn't highlight the selected meeting (the slot does).
- Trail and settle arc have no ambient variants yet (A1–A3 needs `Variant` work).

**Phase 1 status:** all design risks that the emulator can answer are answered. Remaining: real-watch checks (edge 13, lane taps 17, battery, calendar sync 11, private flag 16), Play/publishing (12), and the validator (blocked on permission).

### 6.3d Spike 4 (2026-10-25): center content and time-aware rules on the watch

The data source now runs a first Kotlin port of the prototype's rules engine (`ScheduleRules.kt`: W1–W10, C1, C7, C8, C12) at the current time, and the face shows the prototype's center content. Verified on the emulator at 10:20 AM (debug time offset; see below):

| Prototype feature | On the watch |
|---|---|
| Day + date above the clock (C1) | ✅ "SUN · OCT 25" via `icuText`, 24 px muted; shrinks to 90 % when selected |
| Start/end time (C4, C8) | ✅ "12:30 – 1:00 PM" under the title |
| Look-ahead arrow (C12) | ✅ Amber arrow before the title while a meeting is live (Roadmap 10:00–11:30, elapsed part dimmed) |
| Private (C13) | ✅ Grey lock + "Private", "Details hidden" |
| Double booking (C7) | ✅ Coral stripes on the ring, and under the title a coral overlap icon + the other meeting ("⊚ Vendor call"); tapping 1 o'clock again shows the other one |
| Later lane (blue arc, R5) | ✅ "Late sync" 11:00–11:45 PM on the thin blue lane |

**How icons work:** WFF can't measure text and ignores a second colored `Font` run in one line, so the data source measures each line with the watch's default typeface and sends the width in the RANGED_VALUE's MAX (with flags); the face draws the icon as shapes just before the centered text (RULES §8).

**Consequences of adding these:**
- **One more slot:** `details` (5 of 8 used).
- **Two slots update separately** (status and details), so on a slow watch the title and the place line could change a frame apart.
- **Time-aware slots refresh every 5 minutes** (the minimum), so past/live/next/lane stay current; a small extra battery cost, to measure on a real watch.
- **Width measurement assumes** the data source's default typeface is the face's `SYNC_TO_DEVICE` font. Both run on the same watch, so they should match; verify on a Galaxy Watch (Samsung's One UI font).
- **Packing numbers into MAX** is a contract between the two apps; documented in RULES §8 and `SpikeSchedule.kt`.
- Debug only: `app://io.github.karthikeyags07.scheduleclock/time?offset=-720` shifts the data source's clock 12 h (the dial looks the same) so daytime rules can be tested when the emulator says evening.

**Not yet on the watch:** AM/PM next to the clock, the later-lane time-of-day icon (C5), "Tomorrow ·" styling with real tomorrow data (C6), the empty-day centered clock (C3), ambient variants (A1–A5), precise lane taps (I11).

### 6.3e Spike 5 (2026-10-25): scrolling long titles (C9a), written in the cloud, **built and verified 2026-10-26** (results below)

Written without an Android SDK (the session could not download one), so none of it has been compiled or seen on the emulator. Code: `SpikeSchedule.titleScroll`, `TextFit.titleMinWidth`, two new data-source services (slots 4 and 6 of the face; 7 of 8 slots now used), the title blocks in `gen_spike_face.py`, and a fake "Quarterly roadmap and budget alignment with leadership" meeting at 5:30 PM.

How it works: WFF has no marquee, so the full title is drawn by its own slot whose `BoundingBox` crops it to the title window, and its x is a `Transform` driven by `[MINUTE] * 60000 + [SECOND_MILLISECOND]`: hold 1.4 s, glide at 30 px/s (smoothstep), hold 1.3 s, fade out, jump back, fade in. The status slot hides its own (shortened) title while scrolling and shows it alone in ambient (`Variant mode="AMBIENT"`).

Things to check on the emulator, in this order:

| # | Check | If it fails |
|---|---|---|
| 1 | The project still builds and the face loads (new `BoundingBox`, `Variant`, `floor`/`clamp`/`<` in expressions) | The error names the element; most likely `Variant` placement or expression syntax |
| 2 | `BoundingBox` really crops the scrolling text to x 95–355 (it did for ovals) | Cover the edges with black shapes drawn above |
| 3 | `[SECOND_MILLISECOND]` runs 0–59999 and the scroll is smooth, not stepping once a second | Fall back to `[SECOND]` (1 Hz steps), or scroll in whole-second steps |
| 4 | Expression length is accepted (each scroll expression repeats the time term several times) | Move the shared term into a `UserConfiguration`-free helper or shorten the formula |
| 5 | Battery: the per-frame expression runs only while a long title is on screen; measure CPU/min like spike 3's trail | Scroll once per minute, or only when selected |
| 6 | Ambient: shortened title only, no scroll (A2a). The rest of the face has no ambient design yet | Ambient is a separate task |
| 7 | Selected state: the scrolling title moves up 30 px with the details but is not enlarged ×1.15 (the crop window is fixed) | Accept for now |
| 8 | The title is shrunk 30 → 24 px in the prototype but the watch only truncates at 30 px; a title that fits at 24 but not 30 still shows an ellipsis | Needs a per-meeting font size; a later spike |

**Results on the emulator (2026-10-26):**

| # | Check | Result |
|---|---|---|
| 1 | Builds and loads | ✅ Built first time; the face loads and redraws |
| 2 | `BoundingBox` crops the scroll | ✅ Text clipped to its window |
| 3 | Smooth scroll | ❌ → ✅ **Fixed.** `[SECOND_MILLISECOND]` is **seconds with a fraction** (on-screen probe read `44.543`), not 0–59999 ms, so the text didn't move (0 px over 12 s). Now `([MINUTE] * 60 + [SECOND_MILLISECOND]) * 1000`; measured 4–9 px every ~140 ms (~30–45 px/s with the ease), moving every frame |
| 4 | Expression length | ✅ Accepted |
| 5 | Battery | ⚠️ → ✅ **Fixed.** A scroll slot with data makes the runtime redraw ~10×/s: **0.41 s → 3.2–3.7 s CPU per minute** with the screen on, even when no long title was showing (the slots sent empty text). Now they send **no data** unless they're scrolling: **0.63 s/min** without a long title, ~3.5 s/min only while one is on screen. Ambient unaffected (10–50 ms/min) |
| 6 | Ambient | ✅ Static "Quarterly roadma…", no scrolling |
| 7 | Selected state | ✅ The scrolling title sits between the divider and the time range |
| 8 | 24 vs 30 px | ⚠️ As noted: ambient and non-scrolling titles are 30 px; the scroll is 24 px |

Also verified: the AM/PM label (C11) sits after the time ("11:32 PM"). Debug note: with the `time?offset` deep link active, AM/PM shows the real clock while meetings use the shifted time.

### 6.3f Phase 2 start (2026-10-27): reading the calendar on the watch

`CalendarSource` reads two providers, merges them and drops duplicates (same start, end, title). It skips declined and cancelled events, marks private/confidential events private (C13), and reads all-day events by their UTC date (W9). The face defaults to the real calendar (`day?name=calendar`); `busy` and `example` remain for testing. Without access the details read **"Allow calendar · Tap the ring to set up"**, and a ring tap opens Android's permission request (`PermissionActivity`, also in the app list as "Calen-Time"). Refresh: every 5 min (the slots' update period) and on taps; calendar edits are not pushed yet.

| Where | Result |
|---|---|
| Emulator (Wear OS 6) | ✅ Permission prompt, then 4 test events on the face (live, double booking, private; the cancelled one left out); W5a lane hold before 6 AM |
| Galaxy Watch8 (SM-L320) | ❌ **No calendar data reachable.** The standard provider (`com.android.calendar`) has no calendars; Wear OS's phone-calendar copy (`com.google.android.wearable.provider.calendar`) is empty, because Galaxy watches sync through Galaxy Wearable, not Google's companion; Samsung Calendar keeps events in its own provider (`com.samsung.android.calendar.watch`), which refuses other apps (SecurityException) |

**Decided 2026-10-27: (a) phone companion app** (§6.2). Options considered: Options: (a) a **phone companion app** that reads the phone's calendar and sends the next two days over the Wearable Data Layer: works with any Android phone and any Wear OS watch, but adds a phone app and its listing/privacy work; (b) **Google Calendar on the watch**, if it fills the standard provider (to test); (c) Samsung-only access: not available to third parties.

### 6.4 Minimum OS

Wear OS 5+ (WFF v2, needed for `WeightedStroke` on `Arc`): Galaxy Watch 4 and later on current software, and Pixel Watch 1 and later.

### 6.5 Phone app and release (designed 2026-10-30, not built yet)

Plan: release Calen-Time as **one Play listing** with a phone app and a small watch app. The watch app installs the watch face itself through **Watch Face Push** (Wear OS 6+; one face per app, which is enough because the looks are colourways), so the face needs no listing of its own. iPhone is out: Galaxy and Pixel watches only pair with Android, and Apple Watch doesn't allow custom faces.

The phone app (rules in RULES §12) has a first-run setup and four tabs: **Today** (watch preview, connection, Sync now, today's meetings), **Calendars** (pick calendars per account), **Watchface** (colourways) and **Settings** (buzz switches). Design prototype: `prototype/phone.html`.

Build order: (1) calendar picker, (2) Watch Face Push install on the emulator, (3) colours and buzz settings sent from the phone, (4) Play release (validator token, privacy policy, listing).

Open: Wear OS 5 watches can't use Watch Face Push; decide whether to also keep a normal Play listing for the face.

## 7. Tooling requirements

This Mac: Apple silicon (arm64), macOS 26.6, 16 GB RAM, Homebrew. **Android setup done 2026-10-25** (see 7.0a).

| Tool | Needed for | Status |
|---|---|---|
| **Android Studio** 2026.2.1 | Building both packages; bundles the JDK (JBR, OpenJDK 25), Gradle and the emulator UI | ✅ Installed via Homebrew (`android-studio` cask) |
| **Android SDK** in `~/Library/Android/sdk` (~8 GB): platform-tools 37.0.1, emulator 37.2.12, build-tools 37.0.0, platforms android-36 (Wear OS 6) + android-34 (Wear OS 5), Wear OS arm64 system images for both | Compiling and running on emulators | ✅ Installed with Google's SDK command-line tools (`android-commandlinetools` cask). Licenses accepted: Android SDK, SDK preview, ARM DBT |
| **WFF validator + memory footprint tool** (Google's `watchface` tools) | Play requires faces to pass these | ❌ I'll set up once Android Studio is in |
| **Real Wear OS watch** (Galaxy Watch 4 or newer on Wear OS 5+), Developer options + ADB over Wi-Fi | Tap latency, calendar sync, private flag, edge visibility, battery (risks 1, 11, 13, 16, 17) | ❓ Do you have one? |
| **An Android phone** paired with the watch, signed in to the calendar(s) you use | Galaxy Watch 4+ and Pixel Watch **can't pair with an iPhone**; the watch gets its calendar from the paired Android phone | ❓ Needed for real-device testing |
| **Google Play Console account** ($25 one-time, identity verification) | Publishing | ❓ Before release. New personal accounts must run a **closed test with at least 12 testers for 14 days** before production (to re-check when we get there) |
| **Privacy policy URL** + Play "Data safety" form | Calendar access is sensitive data; Play requires both | ❓ Can be a page on the GitHub Pages site |
| **Store assets**: icon, face screenshots, short/long description, package name (e.g. `io.github.karthikeyags07.scheduleclock`) | Play listing | ❓ Later |
| **Python 3** | Serving the prototype locally (`python3 -m http.server`) | ✅ Built into macOS |
| **Figma** | Visual design, if we move there | ✅ Installed; Figma MCP needs authorizing via `/mcp` |
| Samsung **Watch Face Studio** | Optional WYSIWYG WFF editor | Optional, not needed |
| **Anta** font (Google Fonts, OFL) | Clock digits and dial numbers (RULES T5). Prototype loads it from Google Fonts; the WFF build bundles `Anta-Regular.ttf` | ✅ Bundled in the face: `android/watchface/src/main/res/font/anta_regular.ttf` (from google/fonts, license in `android/watchface/ANTA_FONT_LICENSE.txt`); WFF uses `family="anta_regular"` |

### 7.0a Local setup (done 2026-10-25)

| Item | Detail |
|---|---|
| Emulator `Wear_OS_6_Large_Round` | Wear OS 6 (API 36, Android 16), 454 × 454 round, 320 dpi. **Verified:** boots in ~45 s, shows up as `emulator-5554` in `adb` |
| Emulator `Wear_OS_5_Small_Round` | Wear OS 5 (API 34), 384 × 384 round, 320 dpi (oldest supported, small screen) |
| Command-line env | `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` · `export ANDROID_HOME="$HOME/Library/Android/sdk"`. Not added to `~/.zshrc`; Android Studio finds both on its own |
| Start an emulator | `"$ANDROID_HOME/emulator/emulator" -avd Wear_OS_6_Large_Round`, or Device Manager in Android Studio |
| Gotcha | **Emulator clock falls behind** after the Mac sleeps (it paused, and doesn't catch up even with automatic time; seen 7 h 50 min behind on 2026-10-27). The image has no root, so the clock can't be set, and `adb reboot` hung. Fix: cold boot: `adb emu kill`, then `"$ANDROID_HOME/emulator/emulator" -avd Wear_OS_6_Large_Round -no-snapshot-load`; the clock then matches the Mac |
| Gotcha | If an emulator says "Cannot find AVD system path", check `image.sysdir.1` in `~/.android/avd/<name>.avd/config.ini` starts with `system-images/` (no `sdk/` prefix) |
| Real watch | **Galaxy Watch8 40 mm (SM-L320)**, Wear OS 6 (API 36), 438 × 438. Connected 2026-10-27 over Wi-Fi: on the watch, Developer options → ADB debugging + Wireless debugging → Pair new device; then `adb pair <IP:port> <code>` once, and `adb connect <IP:port>` (the main Wireless debugging screen's port, which changes after a restart). Install with `adb -s <IP:port> install -r …` (`-e` fails while the emulator also runs). `DEBUG_SURFACE set-watchface` works on it. The orange dot at 9 o'clock is Samsung's notification indicator, not the face |
| Sample days | Debug links: `app://io.github.karthikeyags07.scheduleclock/day?name=busy` (default: a dozen meetings rolling from the current hour, remade once they've all ended) or `…/day?name=example` (fixed times). Send with `adb shell am start -a android.intent.action.VIEW -d <link>` |
| Note | `sdkmanager` (CLI) is deprecated in favour of the new `android` CLI and its installed-package list is unreliable; check folders under the SDK instead |

**Where to build:** the Android build and emulator must run on this Mac (a local session). Cloud sessions can keep editing the prototype and docs.

### 7.0 Build plan

| Phase | What | Done when |
|---|---|---|
| 0. Setup | Install Android Studio + SDK + Wear OS emulator; create the Gradle project with two modules: `watchface` (WFF XML, no code) and `datasource` (Kotlin) | An empty face installs on the emulator |
| 1. Spike | Prove the risky parts with fake data: `Launch` deep-link tap → redraw speed (risk 1), animation on complication changes (2), clock reacting to `selected` (3), transparent gaps + element limit (8, 9), private stripes (15), lane → ring slot (4), edge visibility (13) | Each risk marked go / fallback in §6.3 |
| 2. Data source app | Port the prototype's rules engine (lanes, clashes, next meeting, qualifiers; RULES §1 and §3) to Kotlin; read the watch calendar (`READ_CALENDAR`); publish the 8 complication slots with timelines; the invisible tap activity | Real calendar shows correctly on the emulator |
| 3. Watch face | Full WFF layout from RULES: ring, lane, ticks, numbers, Anta clock, details, motion, ambient, dim/red modes, 12 + 12 tap hotspots | Matches the prototype side by side |
| 4. Test | Emulator + real watch: rule checklist, validator, memory footprint, battery in always-on | Validator and memory checks pass |
| 5. Publish | Play listing(s), privacy policy, data safety, closed test (12 testers × 14 days), production | Live on Play |

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
- **v0.25:** Softer meeting arcs (60 % opacity, tunable slider); Dim (low power) toggle; Tactical red toggle (A4, A5, R6a).
- **v0.26:** Trail is now fixed dashes that light up as the now dot passes and fade behind it, instead of a glow that travels with the dot (R6).
- **v0.27:** Tomorrow's meetings stay on the thin lane until the clock passes midnight, then all move to the main ring together. Simulated time now runs past 12 AM into the next day (W5).
- **v0.28:** Trail dashes doubled to 20 per hour (240, every 1.5°) and thinned to 1.2 px (R6).
- **v0.29:** Arcs are solid and darker instead of see-through; private stripes and the double-booking gap get a dark fill instead of transparency (R6a).
- **v0.30:** Prototype control panel redesigned into grouped sections (Time, Day, Display, Behavior) with switches and a segmented day picker; the complication payload view is removed. New **Dial only** mode (button, `F` key, or `#dial` in the URL) hides all controls and shows just the watch.
- **v0.44:** True-size scale now comes from the screen's resolution: native pixels (CSS size × pixel ratio) are matched to a table of known panels (Galaxy Z Fold5–8 Ultra and Flip7/8 inner and cover screens, iPhones, iPads, MacBooks), and CSS px per inch = CSS diagonal ÷ real diagonal, so it follows browser zoom and folding/unfolding. The Android model comes from client hints when Chrome hides it; a shared resolution (Fold7 cover vs Flip inner) without a model averages and says "may be off". Unlisted phones still use an estimate. Calibration uses the card's long edge on screens wide enough. Fold8 / Fold8 Ultra sizes are from press listings, not yet checked on a device.
- **v0.64:** Breathing space on the lane → ring move (W8): a meeting joins the main ring only 30 min after the now dot has passed its slot (the 7–8 PM call moves at 8:30 AM, not 8:00). Prototype first, then on the watch (v0.66).
- **v0.65:** Now dot about 40 % larger (13 px dot, 19 px ring, 28 px halo; was 9 / 14 / 22) so it's easier to see, on the watch, prototype and Figma SVGs (R6).
- **v0.66:** The 30-minute wait before a lane meeting moves to the main ring (W8) is now on the watch too; tested on the emulator (the 7–8 PM call stays on the lane at 8:15 AM and is on the ring at 8:35 AM).
- **v0.67:** Phone app designed as a clickable prototype (`prototype/phone.html`): setup, Today, Calendars, Look, Settings, with switches for each state; release plan via Watch Face Push (§6.5, RULES §12).
- **v0.68:** The phone app's watch previews are now the real watch face: the watch prototype runs inside each preview in a new embed mode (`index.html?embed&time=…&theme=…&face=…#<meetings>`), so the ring, ticks, trail, fonts and Time-Shift colours match exactly. The Today preview can be tapped like the watch.
- **v0.69:** The Time-Shift colourway is renamed **Solaris** (Latin for "of the sun") everywhere: phone app, watch prototype, watch Customize screen, Figma SVG and RULES §10. Earlier changelog entries keep the old name. Phone app navigation is icons only, taller (81 px), with the selected icon in the accent colour; the Today heading and date are gone; the Solaris card text is one line.
- **v0.70:** Phone app: the Look tab is now **Watchface**; the selected colourway card has a subtle 1 px border; more space between each preview and its name.
- **v0.71:** Phone app tidy-up and setup fallback: Calendars and Watchface lose their subtitles and the account letter circles; the confirm step shows the real face with the watch's question below it; a new **It didn't show up** path explains how to pick the face on the watch and offers **Send it again** (RULES A1).
- **v0.63:** Time-Shift day colours revised (RULES S3) so each part of the day is distinct: midday goes cream → honey → amber instead of one gold, and the night goes purple → royal blue (9 PM) → indigo (midnight) → cobalt (3 AM) → azure instead of one blue; night stops are clock times. Prototype and watch; installed on the Galaxy Watch8.
- **v0.62:** Time-Shift is now a **colourway** (Colours → Time-Shift) instead of a separate Style option: each colourway tints the theme arcs panel and the Time-Shift panel, hiding the unused one with a transparent tint. Verified on the emulator (Amber and Time-Shift); installed on the Galaxy Watch8.
- **v0.61:** **Time-Shift on the watch** (RULES §10): Style → Time-Shift in the watch's customize screen colours main-ring meetings by the light at their minutes (sunrise/sunset computed on the watch for the time-zone location). The ring image gains a fourth panel (102 ms to draw all four). Verified on the emulator; installed on the Galaxy Watch8. Location stays the time-zone guess for now (decided 2026-10-28); real location is a possible later addition.
- **v0.60:** **Colour themes on the watch** (RULES §9): Amber, Mint, Violet and Rose under Colours in the watch's customize screen. The ring image becomes three panels (themed arcs, themed lane, fixed colours) that the face tints and shows one at a time, so no extra slot is used. All four verified on the emulator. The face's info file still said `Editable=false` (from the spike), which hid Customize; now `true`: the editor shows "Colours: Amber", changed with the crown. Confirmed on the Galaxy Watch8: Customize shows Colours and the theme changes.
- **v0.59:** **Meeting buzzes** on the watch (RULES §11): one 0.75 s buzz 2 min before a meeting, one 1.5 s buzz halfway, two 0.75 s buzzes 5 min before the end; exact alarms re-armed on calendar changes, restarts and clock changes. Patterns verified on the Galaxy Watch8 (0.8 / 1.55 / 1.96 s at alarm strength). Scheduled firing verified on the emulator: the start buzz fired 1.4 s after its minute and set the next buzz. Uses `USE_EXACT_ALARM`, which Play allows for calendar apps; to be justified in the Play listing.
- **v0.58:** New **Time-Shift** face in the prototype (Face picker): main-ring meetings take the colour of the light at their hour, from sunrise/sunset computed on the device for the user's location (or the time-zone guess), with a day colour bar and "Use my location" in the panel; demo day "Around sunset" (RULES §10, S1–S4). Prototype only; watch to follow once approved.
- **v0.57:** Tap the enlarged meeting details to go back to the normal view (I8), on the watch (new details tap area → `app://…/release`) and in the prototype. Verified on the watch via its link; a finger tap on the details is still to be confirmed. Also: "Tomorrow" without a trailing dot when a meeting has no place (C6).
- **v0.56:** **Real calendar on the real watch, end to end:** an event added on the Fold7 was picked up by the calendar-change trigger, sent automatically, and drawn on the Galaxy Watch8 (arc and details) within seconds. Phase 2's core path is done; still open: Outlook-only users must turn on Sync calendars (the app says so).
- **v0.55:** Empty day on the watch (C3): with no meetings the date and clock move down to the center and the clock grows to 115 %, the divider fades, no message; it animates back when meetings appear (M4). Verified on the Galaxy Watch8 with the phone app sending 0 events.
- **v0.54:** Phone app tested on a Galaxy Z Fold7 (Android 17) with the Galaxy Watch8: calendar access, background sync and the Data Layer work (phone → watch in ~0.2 s; watch shows as connected). The phone's shared calendar had **no events**: meetings in Outlook stay inside Outlook unless its "Sync calendars" setting is on, so the app now explains this when it finds nothing to send.
- **v0.53:** **Phone companion app** (new `phone` module): reads the phone's calendar and sends yesterday → tomorrow to the watch over the Wear OS Data Layer, on open, on calendar changes and every 15 min; private events are sent without title or place. The watch receives them (`PhoneCalendarListener`), keeps a copy, and merges them with its own calendar. With no calendar at all the face says "Open Calen-Time on your phone to sync". Watch side installed on the Galaxy Watch8; phone side built, not yet installed or tested.
- **v0.52:** Phase 2 started: the face reads the watch's calendar (standard + Wear OS providers), asks for calendar access from the face, and defaults to the real calendar; sample days stay as debug options. Verified on the emulator. **On the Galaxy Watch8 there is nothing to read:** Samsung keeps calendar events in a private provider (§6.3f, risk 18); the watch stays on the busy sample until that's decided.
- **v0.51:** Watch titles now shrink 30 → 24 px before scrolling (C9): "→ Roadmap planning" shows in full instead of "Roadmap plan…". Verified on the Galaxy Watch8 with a 24 px title, a scrolling long title and a 30 px short one.
- **v0.50:** First run on a real watch (Galaxy Watch8 40 mm, Wear OS 6): installs, sets, taps work. The watch's sample is now a **busy day that rolls with the clock** (live meeting, back-to-back, private, full and partial double booking, long title, one past the window) so it looks busy at any hour; the fixed-time day stays as `day?name=example` (§7.0a). Fixed the Kotlin rules missing W5 (tomorrow's meetings took the main ring before midnight) and times before today's midnight. Known gap: the watch ellipsizes a title that would fit at 24 px instead of shrinking it (C9's 30 → 24 px step isn't built).
- **v0.49:** Night meetings before 6 AM are held on the lane too, so nothing is on the main ring from 12 to 6 AM (W5a). Watch sample day gains a 12:30–2:00 AM "Night call" to test it; verified on the emulator at 1:25 AM (live night call on the lane, look-ahead arrow to Standup).
- **v0.48:** From 12 to 6 AM the day's meetings stay on the thin later lane instead of the main ring, and all settle onto the ring at 6 AM (W5a). Only meetings that fit completely are shown (no clipped slivers), never past 6 PM so lane arcs don't overlap. Night meetings before 6 AM behave as before. Prototype and watch (Kotlin rules); verified on the emulator at 1:21 AM and with a +5 h offset.
- **v0.47:** Selecting a meeting keeps its arc the same width and colour at full strength and dims everything else to 35 % (no more 22 px highlight) (R9). Prototype and watch; on the watch the ring image draws the selection and the selected-arc slot is gone (6 of 8 slots used, 2 free). Verified on the emulator.
- **v0.46:** Every meeting on an all-day event's day now counts as clashing with it: coral stripes on its arc (coral lane arc), an overlap icon and the all-day event's name on its clash line (W9, R4, C7). Prototype and Kotlin rules; watch part not built.
- **v0.45:** All-day events are kept as events but never shown on the face (W9, Q5 decided): filtered out of the rules in the prototype (demo "Company holiday" and a note in the panel) and in the Kotlin rules engine (`Meeting.allDay`, set from the calendar's ALL_DAY flag). Not yet built on the watch.
- **v0.43:** Prototype opens at the device's real local time and date and follows the clock (Live); moving the slider or pressing Play simulates, Live returns. The slider now spans 12 AM to 6 AM next day. The watch already reads its own clock (a debug time offset is only applied if set).
- **v0.42:** Project renamed **Calen-Time** (calendar + time): titles, watch face and app labels, Figma SVGs. Repository, Pages URL and Android package ids unchanged.
- **v0.41:** All arcs have round ends (R1a), in the prototype, the ring image and the selected/settle arcs on the watch (unbuilt). Prototype gains a **Colors** section: themes (Amber real; Mint, Violet, Rose are examples), per-element colour pickers, Copy theme, saved Custom theme (RULES §9). Watch colour configurations are planned, not built: waiting for the colours.
- **v0.40:** Spike 5 verified on the emulator: AM/PM label works; scrolling titles fixed (`[SECOND_MILLISECOND]` is seconds, not ms) and made cheap (scroll slots send no data unless scrolling: 0.63 vs 3.5 s CPU/min) (§6.3e).
- **v0.39:** Watch-face spike 5 written (unbuilt): long titles scroll in their own cropped slots (4 and 6), ambient shows the shortened title; checklist in §6.3e. Fake long-title meeting added.
- **v0.38:** Long titles scroll slowly through a fixed window instead of ending in an ellipsis; ambient shows the first characters and "…" (C9a, A2a). Prototype only (new "Long titles" demo day). **Not on the watch yet:** WFF has no marquee, so the face needs a cropped title text moved by a seconds-based `Transform` plus a second, truncated title for ambient, which means a new complication field or slot (slot 6 of 8); the data source must send the full title and its width. To spike on the emulator first.
- **v0.37:** AM/PM label on the watch face (C11), muted 24 px right of the time. WFF can't measure text, so the time is right-aligned to a fixed seam and both shift 16 px for two-digit hours; widths come from the prototype, **not yet checked on the emulator**. The time is forced to 12-hour (`hourFormat="TWELVE"`) so it always matches AM/PM; AM/PM text comes from the watch locale. The `TWELVE` value is from memory of the WFF reference, so confirm it builds.
- **v0.36:** Spike 4: date, time range, place line, look-ahead arrow, private lock and the double-booking line on the watch, driven by the rules engine ported to Kotlin; icons placed from data-side text widths; `details` slot added (5 of 8) (§6.3d).
- **v0.35.3:** Fixed a stray arc sweeping across the face on taps: the selected arc's angles no longer animate; switching meetings fades the old arc out in place, then the new one in (~400 ms apart, M2). The ring image only dims the others. Verified frame by frame on the emulator.
- **v0.35.2:** Spike face gains the clock/details divider (T2), animated with the selection (M1: up 18 px, 60 % wide; the prototype's 32 px fits its different layout).
- **v0.35.1:** Spike face gains the dial: 15-minute ticks (R7), 1–12 numbers and clock in bundled Anta (R8, T5).
- **v0.35:** Spike 3: selected arc slides and grows on its own slot, lane → ring settle animates, comet trail runs face-side for ~0.3 s CPU/min; `CUBIC_BEZIER` controls must be space-separated (§6.3c).
- **v0.34:** Spike 2: the ring is one image drawn by the data source (`SMALL_IMAGE`, PHOTO style). No meeting limit, private and clash stripes included; ~170 ms tap-to-face, +2 MB, ~+35 ms CPU per update (§6.3b). Slot plan updated (RULES §8).
- **v0.33:** Phase 1 spike 1 on the emulator: taps, animation, clock-in-slot, default provider and gaps pass; weighted elements capped at 7 (fail) → propose a single ring image (§6.3a).
- **v0.32:** Phase 0 setup: Android Studio, Android SDK, Wear OS 5 and 6 emulators installed and created (§7, §7.0a).
- **v0.31:** Build readiness: tooling list checked against this Mac, Android-phone and Play testing requirements added, phased build plan (§7.0).
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
