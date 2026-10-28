# Calen-Time: Behavior & Design Rules

> **Source of truth** for what the watch face does in every situation, and why.
> Updated whenever a design decision changes. The working reference is [`prototype/index.html`](prototype/index.html); product context, platform and risks are in [`PRD.md`](PRD.md).
> Last updated: 2026-10-24

All sizes are in px on the **450 × 450 face canvas** (≈ 2 px per dp on a typical Galaxy Watch). Rule IDs (W1, R4, …) are stable; refer to them in discussions.

---

## 0. Design principles

| # | Principle | What it means in practice |
|---|---|---|
| P1 | **Glance first** | Time and the next meeting must be readable in under 2 seconds. Everything else is secondary. |
| P2 | **One layout, many sizes** | There are no separate screens. The same elements resize and move between states (default, selected, empty). |
| P3 | **Position = time** | Meetings sit at their real positions on a 12-hour dial. 1–2 PM is always between the 1 and the 2. |
| P4 | **One question per visual channel** | **Lane / radius** = when (soon vs later) · **contrast** = emphasis (the selected meeting stays at full strength, everything else dims) · **color** = state (amber normal, coral clash, blue later, dim = elapsed) · **shape** (split band) = double booking. A channel never means two things. |
| P5 | **Never color alone** | Every color meaning is paired with a shape or position difference (coral is also split; blue is also thin and on its own lane). |
| P6 | **Nothing collides with "now"** | No meeting is ever drawn at the same dial position as the now dot, its trail, or an elapsed meeting. |
| P7 | **24 px minimum text** | No text below 24 px (≈ 12 sp). Long text gets an ellipsis rather than shrinking. |
| P8 | **Quiet when empty** | No filler messages. With nothing scheduled, the face is simply a clock. |
| P9 | **Buildable in Watch Face Format** | Every rule must map to WFF primitives fed by complication data (see §8). |
| P10 | **Private stays private** | A watch face can be seen by anyone nearby. Details of meetings marked private in the calendar never reach the face: the data source strips them before sending (C13). |

---

## 1. Time window & lanes

| # | When | Then | Why |
|---|---|---|---|
| W1 | Always | The **main window** is the next 12 hours: `[now, now + 12 h)`. | A 12 h dial can only show 12 hours without positions repeating. |
| W2 | A meeting is in progress | The window starts at **that meeting's start** instead of now, and its end moves back by the same amount. | The elapsed part stays visible (R3) without colliding with the far end of the window (P6). |
| W3 | A meeting **ends within** the window | It goes on the **main ring** (12 px, amber). | It fits completely, so its dial position is free. |
| W4 | A meeting is today but **doesn't end within** the window (e.g. 7–8 PM, seen at 6 AM) | It goes on the **later lane** (thin, blue), at its real clock position. | On a 12 h dial, 7 PM and 7 AM share an angle. A separate lane keeps them apart (P6). |
| W5 | A meeting is **tomorrow** (starts after the next midnight) | **Always on the later lane until 12:00 AM**, even if it would fit the 12 h window. At midnight it becomes today's meeting; it moves to the main ring at 6 AM (W5a, M3). All of tomorrow's meetings that are on the lane move together at that moment. | The ring means "today"; tomorrow stays small and blue until the day actually changes. |
| W5a | It's between **12:00 and 6:00 AM** | The day hasn't started yet: **nothing takes the main ring**, night meetings (even a live one) included. Every meeting stays on the **later lane** once it ends within the 12 h window (never past 6 PM, so two lane arcs never share an angle; never a clipped sliver). Further-out ones appear as the clock moves. At **6:00 AM** they all move to the main ring together (M3), and anything past the 12 h window stays on the lane (W4). | In the small hours the ring shouldn't look like the day has begun; the morning stays small and blue until you're up (added 2026-10-27). |
| W6 | A meeting is tomorrow and starts **after** the window | **Toggle "Tomorrow look-ahead 14 h" (default on):** if it starts within **14 hours of now** it shows on the later lane, in the list, with "Tomorrow ·"; it moves to the main ring once it fits (W5). Further out: **not shown** until it comes into range. Toggle off: only what starts inside the 12 h window (P8). | Tomorrow's 8 AM should appear in the evening (from about 6 PM), not at breakfast time. It must not clutter the face all day. |
| W7 | A meeting has ended | Not shown. | Past is noise on a glanceable face. |
| W8 | A later-lane meeting comes to **fit completely** in the window (end ≤ window end) | It **moves to the main ring**, animated (M3). Example: the 7–8 PM call moves at **8:00 AM**, after the now dot and trail have passed the 7→8 slot. | Never shows as a clipped sliver, and never shares a slot with now (P6). |
| W9 | An event is an **all-day event** (the calendar's all-day flag, not its times) | It is **kept as an event but never drawn or listed itself**: no arc, not selectable, never "next". But **every meeting on its day clashes with it** (R4, C7): each meeting's arc gets the double-booking coral stripes (a lane meeting's thin arc turns coral), and its clash line shows an overlap icon and the all-day event's name ("Company holiday"; a real double booking is named first, the all-day event counts in the "+n"). | The whole day is spoken for, so any meeting on it conflicts. It has no time on a 12 h dial, and a whole-day banner would bury the meetings. Decided 2026-10-26 (PRD Q5). The calendar stores all-day events as midnight-to-midnight in UTC, so the flag must be used: in India their times fall on the wrong side of local midnight. |
| W10 | List order (for "next" and for taps) | **Chronological across both lanes.** | Indexes don't shift when a meeting changes lane, so a selection never jumps. |

---

## 2. Ring drawing

| # | When | Then | Why |
|---|---|---|---|
| R1 | Always | **Dial:** 0° = 12 o'clock, clockwise, 12 hours per turn. **Track:** 12 px, `#1c1c1f`, radius **R = 219**, so the band (213–225) is **flush with the screen edge**. | Familiar clock geometry (P3). Using the very edge gives the center content the most room and makes the ring read as the watch's own bezel. |
| R1a | Always | **Every arc has round ends** (main ring, elapsed and remaining parts, later lane, double-booking stripes). A round cap reaches half the stroke width past the path, so each path is shortened by that angle (about 1.6° on the 12 px band, 0.55° on the 4 px lane): the visible rounded ends stay at the meeting's start and end, and a very short meeting becomes a dot. The back-to-back gap (R2) is unchanged. The dashed private lane and the double-booking mask stay square. | Softer look; the geometry stays honest. |
| R2 | Two meetings are back to back | **1° gap** at each arc end. | Adjacent meetings read as separate (WFF: `discreteGap`). |
| R3 | A meeting is in progress | **Elapsed** part (start → now) is dim amber `#6e4f22`; the **remaining** part is amber `#ffb547`; the now dot sits between them. | Shows how far into the meeting you are. |
| R4 | Two or more main-ring meetings **overlap** | Only the **overlapping minutes** become **two 5 px coral stripes** (`#ff7a59`) at R ± 4.5, with a 4 px black gap, inside the normal 12 px band (the outer stripe's last ~1 px falls past the screen edge). Elapsed overlap is dim coral `#7a3b2c`. The non-overlapping parts stay amber. 3+ overlaps still use two stripes. | Shape (split = "two things here") plus color (coral = attention) (P4, P5). It stays in the band's footprint, so it never reads as the later lane. |
| R5 | Later-lane meeting (W4/W5) | Arc at **R − 10**, **4 px**, `#8fa3d6`, rounded ends. | Thin, cool and inside reads as "further away"; thick, warm and outside reads as "soon" (P4). |
| R6a | Always | Meeting arcs are **solid but darker**, not see-through: amber at 60 % brightness (lane, clash stripes and selection +30 points), so the clock stays the focus and nothing behind the arcs shows through. **Private stripes** have a dark fill between the stripes; the **double-booking** gap between the two coral stripes is dark coral (`#3f1f17`), never empty. The page has a 20–100 % brightness slider. Ambient arcs stay as outlines. | Transparent arcs let the dash ring show through and looked hollow. |
| R6 | Always (not ambient) | **Now marker:** white dot with a soft halo on the main ring. **Illuminated trail:** a fixed ring of 240 thin dashes (every 1.5°, 20 per hour, 1.2 px wide) across the band. Dashes never move; each lights up (10 % → 90 % white) as the dot reaches it and fades back over the next 60° (ease-out curve). Drawn **under** meeting arcs. | Shows time sweeping through the day; the trail only shows over free time. |
| R7 | Always | **Ticks** every 15 min, inside the later lane: hour (10 px, `#8e8e93`, 2.5 px wide), half-hour (6 px, `#4a4a50`), quarter (3 px, `#33333a`). | Three marks between each pair of hours for fine reading, faint enough not to compete. |
| R8 | Always | **Hour numbers** 1–12, 24 px **Anta** (T5), radius R − 40. 12/3/6/9 `#8e8e93`, others `#5c5c62`. | Readable at the 24 px minimum (P7); cardinal hours stand out for orientation. |
| R10 | A meeting is marked **private** in the calendar | Its arc is a **grey caution band**: 5 px `#8e8e93` stripes / 5 px gaps at **45° to the arc**, instead of solid amber (elapsed part: `#48484c` stripes). Same position and thickness as any meeting, so free/busy stays readable. On the later lane: grey dashed (4 on / 3 off). When selected: unchanged, at full strength (R9). Ambient: grey stripes. | Shows "busy, but not for display" at a glance, without color alone (P5): the stripe pattern is the cue. Grey (not amber) keeps private time quiet and visually separate from meetings you can act on (changed 2026-10-24). |
| R9 | A meeting is selected | The selected meeting keeps **its normal look**: same width (12 px on the ring, 4 px on the lane), same colours, elapsed part and private stripes, at **full strength**, drawn above any double-booking stripes. **Everything else** on the ring (other meetings, clash stripes, the later lane) → **35 %**. No thickening. | Contrast alone isolates the selection; the arc doesn't change shape, so the dial's geometry stays steady (changed 2026-10-27). |

**Color tokens**

| Token | Hex | Use |
|---|---|---|
| event | `#ffb547` | Main-ring meeting |
| past | `#6e4f22` | Elapsed part of a live meeting |
| later / laterSelected | `#8fa3d6` / `#c3d0f2` | Later lane |
| laterText | `#a9b8e0` | "Tomorrow ·" text and the time-of-day icons |
| clash / clashPast | `#ff7a59` / `#7a3b2c` | Double-booking stripes, overlap icon and its text |
| private / privatePast | `#8e8e93` / `#48484c` | Private caution stripes |
| privateSel / privateSelPast | `#c7c7cc` / `#6a6a70` | Selected private stripes |
| track | `#1c1c1f` | Ring track |
| text / muted | `#f2f2f2` / `#8e8e93` | Primary / secondary text |

---

## 3. Center content

| # | When | Then | Why |
|---|---|---|---|
| C1 | At least one meeting is shown (either lane) and none is selected | **Date**, **time**, divider, and **details of the next meeting that hasn't started** (first in chronological order, W10). If the only meetings left are in progress, the live one is shown. | P1: what time is it, what's next. A live meeting is already visible on the ring (R3), so the text looks ahead. |
| C2 | A meeting is selected | The details show **the selected meeting**, in the same lines (P2). | |
| C3 | **No meetings** in the window or lane | **Date + time only**, moved down to the center, clock enlarged to **115 %**. **No message.** | P8. (Changed 2026-10-24: "Nothing else today" removed.) |
| C4 | Details layout | Three lines: **title** (30 px white, shrinks to 24 px, then ellipsis) · **time range** (24 px white) · **place** (24 px muted). | Time and place are on separate lines so neither has to drop below 24 px (P7). |
| C5 | The meeting is on the later lane and is **today** | Time line: a **time-of-day icon** then **"7:00 – 8:00 PM"** in `#a9b8e0`. Icon by start time: sunrise (5 AM–noon), sun (noon–5 PM), sunset (5–9 PM), moon (9 PM–5 AM). No "Later" word. The icon also precedes the time for tomorrow's lane meetings. | Matches the blue lane; explains why it's not on the main ring. |
| C6 | The meeting is **tomorrow** | Place line: **"Tomorrow · Café Blue"** in `#a9b8e0`; just **"Tomorrow"** when it has no place. | "Tomorrow · 8:00 – 8:45 AM" doesn't fit the time line at 24 px, so the qualifier goes on the place line. |
| C7 | The meeting is **double-booked** | The clash line moves **above the time range** (order: title, clash line, time range). Clash line: a coral **overlap icon** (two intersecting rings, 22 px) followed by the other meeting's name, **"Vendor call"** (or "Vendor call +1" for 3+), in coral; no "Also" word. Takes priority over C6 and the place. | Who you're clashing with matters more than where. |
| C8 | Time range formatting | AM/PM appears once if both ends are in the same half of the day ("1:00 – 2:00 PM"), otherwise on both ("11:30 AM – 12:30 PM"). | Saves width. |
| C9 | Text too wide | Title: shrink 30 → 24 px, then scroll (C9a; ellipsis only in ambient, A2a). Time and place: ellipsis at 24 px. Max widths: title 260, time 255, place 220. | Keeps text clear of the hour numbers in both default and selected (×1.15) states. |
| C9a | The **title is still too wide at 24 px** (not ambient) | It stays full length and **scrolls slowly through a fixed window** (the same 260 px max width, minus any arrow or lock), instead of an ellipsis: hold 1.4 s, glide at about 30 px/s (ease in and out) to the end of the title, hold 1.3 s, fade out and back in at the start (0.26 s each), repeat. Arrow and lock stay put. With reduced motion it does not scroll and shows an ellipsis instead. | Long names can be read in full without shrinking below the minimum size. Time and place lines keep the ellipsis (C9). |
| C10 | Status line ("NEXT · IN 1H 30M", "2 OF 2 · …") | **Hidden** (your call, 2026-10-24). The data source still produces it. | Less clutter; can return later. |
| C11 | AM/PM | Placed 8 px right of the measured time width; time + AM/PM centered together. | Stays attached to the time whatever its width, and the pair stays visually centered. |
| C13 | The shown meeting is **private** | Title becomes **"Private"** with a grey **lock icon** (16 × 20 px) before it; the time range stays (it's already visible on the ring); the place line reads **"Details hidden"**. Works together with the C12 arrow (arrow, lock, then title, all centered). A double booking with a private meeting reads the overlap icon + **"Private"**. | P10. The time is not a secret (the arc shows it); the title and place are. Showing the time was confirmed 2026-10-24 (PRD Q14). |
| C12 | A meeting is **in progress** (e.g. 9:00–9:30, now 9:10) and the details show the upcoming meeting (C1) | An **amber arrow → (20 px wide, 3 px stroke, `#ffb547`)** sits 10 px before the title; arrow + title are centered together, and the title's max width shrinks by 30 px to make room. It's part of the details block, so it scales and dips with it. Not shown when the details show a live meeting, or when no meeting is in progress. Grey in ambient. | Says "this is coming up, not the meeting you're in" without adding a text label (changed 2026-10-24: the NEXT/NOW label was removed). |

---

## 4. Typography & layout

| # | Rule |
|---|---|
| T1 | **Minimum text size 24 px** (≈ 12 sp). Sizes: clock 88 (Anta, see T5) · title 30 (→ 24) · date 24 · AM/PM 24 · time range 24 · place 24 · hour numbers 24. |
| T5 | **Clock digits and the 1–12 dial numbers use [Anta](https://fonts.google.com/specimen/Anta)** (Google Fonts, Regular); everything else (date, AM/PM, meeting text) stays in the system sans, so numbers read as one family and words stay neutral. Anta is fairly narrow (≈ 177 px for "10:55" at 70 px), so the clock stays at **88 px**. Time + AM/PM are **centered as one unit** (AM/PM 8 px after the measured digit width). **License:** SIL Open Font License, so it can be bundled in the published face. The prototype loads it from Google Fonts; the watch build bundles the TTF. |
| T2 | **Default positions (baselines):** date 122 · time 206 · divider 238 · title 284 · time range 318 · place 348. |
| T3 | **No overlaps:** no text may touch an hour number or other text in any state. Checked automatically in the prototype across 183 states (3 sample days, 17 times incl. live and private meetings, late evening and the widest time "10:55", every selection), including the C12 arrow and C13 lock. The same check confirms no private title or place ever appears on the face. Re-run after any layout change. |
| T4 | Fixed scale origins (WFF `pivotX`/`pivotY`): date (225, 110), clock (225, 170), divider (225, 238), details (225, 310), so changing text never shifts the scaling. |

---

## 5. Interaction

| # | When | Then | Why |
|---|---|---|---|
| I1 | Always (not ambient) | The ring band (radius 162–226) has **12 one-hour tap areas** (12→1, 1→2, …). | WFF allows max 8 complication slots, so taps use 12 `Launch` deep-link hotspots (PRD §6.3). |
| I2 | You tap an hour with **one** meeting | That meeting is selected. | |
| I3 | You tap an hour with **several** meetings (double booking, back to back, or main + later lane) | Each tap moves to the next one: **main-ring meetings first (chronological), then later-lane ones**, then back to default. | Lets you reach every meeting in that hour; near ones first. Example at 6 AM with Gym 7–7:45 AM (ring) and Client call 7–8 PM (lane): tap 7 → Gym, tap again → Client call, again → default. |
| I4 | You tap the selected meeting's hour again (no more meetings there) | Back to default. | |
| I5 | You tap an **empty** hour | Back to default. | |
| I6 | A meeting spans several hours | Tapping any of its hours selects it. | |
| I7 | **8 s** with no tap while selected | Back to default (tunable). | The face returns to its glance state on its own. |
| I8 | You tap the center (the meeting details) | If a meeting is **selected**: back to default (the details shrink back). Otherwise, if the shown meeting is **private**: open the **calendar app** (`Launch target="CALENDAR"`, not built on the watch yet) so details are read there, behind the watch's lock. Otherwise nothing. | The enlarged details are the obvious thing to tap to close them (added 2026-10-27). P10: the face never reveals private details; the app is the only way in. |
| I9 | Ambient mode | Taps do nothing; no selection. | |
| I10 | The selected meeting disappears (ended, or day/time changed) | Back to default. | |
| I11 | You tap **precisely on the thin later lane** (from 6 px inside it to 2 px outside, radius 203–211) in an hour that has a later-lane meeting | Only that hour's **later-lane meetings** are considered: the first tap selects the evening meeting directly (skipping the morning one); tapping it again goes back to default. A tap anywhere else in the band follows I2–I5. | A shortcut for people who can hit it; the double tap (I3) stays the reliable path. The lane zone never extends into the main band, so normal taps aren't stolen. On the watch: 12 extra thin hotspots → `select?hour=N&lane=later`. |

---

## 6. Motion

| # | When | Then |
|---|---|---|
| M1 | **Select / back to the normal view** (520 ms, `cubic-bezier(.4,0,.2,1)`, an S-curve; going back is the reverse) | Time → 55 %, up 10 px · Date → 90 %, stays put (moving it would hit the 11 and 1) · Divider up 32 px, 60 % wide · Details (incl. the C12 arrow) → 115 %, up 30 px · Everything but the selected meeting → 35 % (R9; on the watch the ring image switches at once). |
| M2 | The **shown meeting changes** (selecting a meeting other than "next", or switching A → B) | Text fades out and drops 6 px over **170 ms**, the content swaps, and it fades back in over **280 ms**. The layout keeps scaling, with no second screen (P2). Selecting the meeting that's already shown doesn't dip. |
| M3 | **Lane → main ring** (W8) | The blue arc grows outward from R − 10 to R about the center, 4 → 12 px, blue → amber, **900 ms**, eased with no overshoot (`cubic-bezier(.22,1,.36,1)`), so it "sits down" into the band. |
| M4 | Meetings ↔ **empty** (C3) | Date and clock move down to the center (clock 115 %) and the divider fades, with the M1 timing. |
| M4b | Arcs **appear, disappear and recolor** (new day, new meeting, ambient on/off) | Arcs fade in and out and ease between colors over the select duration, instead of snapping. | Rebuilt-on-every-update arcs looked abrupt. |
| M5 | System **reduced motion** is on | All of the above are instant. |
| M6 | Prototype review aids | **Slow motion ×5** (M1 1.8 s, M2 0.7 s out / 1.2 s in, M3 3.5 s) and **Fast-forward time** (1 simulated min / 40 ms). |

---

## 7. Ambient (always-on)

| # | Rule | Why |
|---|---|---|
| A1 | Black background. Meeting arcs become **3 px grey** lines; elapsed `#4a4a4a`; later lane `#5c5c62`; a double booking shows as two grey lines; a private meeting shows as grey stripes. | Low power, low burn-in. Structure survives without color. |
| A2 | Trail hidden; ticks and numbers at 60 %; time range and place hidden; title stays; the C12 arrow turns grey. | Minimum lit pixels while still showing what's next. |
| A2a | A long title in ambient | No scrolling: the **first characters and an ellipsis** ("Quarterly roadmap a…"). | Nothing moves in always-on mode (power, burn-in). |
| A3 | No selection; taps ignored. | |
| A4 | **Dim (low power)** toggle | The same face, unchanged layout, at about 42 % brightness and 75 % colour. Separate from A1–A3 (outlines only). Tapping still works in the prototype. | A dimmer always-on version that keeps the meetings readable. |
| A5 | **Tactical red** toggle | Everything is drawn in red only; brightness follows each element's luminance, so the clock is brightest, meeting arcs mid, and ticks and the track faint. Combines with Dim. | Night-vision-friendly look. WFF: a second colour configuration. |

---

## 8. Data → face mapping (Watch Face Format)

| Slot | Type | Carries | Drawn as |
|---|---|---|---|
| 1 `ring` | SMALL_IMAGE (PHOTO style) | **The whole ring as one image** drawn by the data source: main-ring meetings, elapsed parts, later lane, double-booking stripes, private caution stripes (R1–R10), and the selection (R9: everything else at 35 %, the selected meeting on top). Grey variant in the ambient image field (A1) | Full-face `PartImage` |
| 3 `details` | RANGED_VALUE | TEXT = the line under the time range: place, the clashing meeting (C7), "Tomorrow · place" (C6) or "Details hidden" (C13). VALUE = selected (0/1) so it moves with the layout; MAX = 1 + measured width + 10000 × kind (0 place · 1 clash · 2 tomorrow · 3 private) | Muted / coral with the overlap icon / blue; a clash line moves up under the title |
| 4 `status` | RANGED_VALUE | VALUE = `selected` (0/1); TEXT = title (or "Private"); TITLE = time range; MAX = 1 + measured title width + 10000 × flags (1 arrow C12, 2 lock C13, 4 clash C7, 8 title scrolls C9a, 32 no meetings C3 (date and clock move to the center), 16 title at 24 px C9: the face draws a 30 px and a 24 px copy and shows one, since a font size can't come from data). **Date, clock, divider, title and time range are drawn inside this slot** so their Transforms can read VALUE (spike 1) | Drives every M1 `Transform`; the arrow and lock are drawn just before the centered title using the width |
| 5 `ring_promote` | RANGED_VALUE | The promoting meeting: MIN/MAX = its angles, VALUE = progress lane → ring; no data otherwise. Stepped by the data source: on the lane → moving → handed to the image | Arc scaled R − 10 → R, 4 → 12 px, blue → amber over 0.9 s (M3) |
| 6 `title_scroll`, 7 `title_scroll_arrow` | RANGED_VALUE | **Long title (C9a).** The full title in TEXT; the face's BoundingBox crops it to a window (x 95–355, or 125–355 beside the arrow) and a time-based `Transform` scrolls it. VALUE = selected (0/1); MAX = 1 + overflow px + 10000 × scrolling. Only the slot matching the arrow state sends data. The status slot's TEXT stays the shortened title (ambient, A2a) and gets flag 8 (title scrolls) to hide itself. | Used by the face as slot ids 4 and 6. |
| 2, 8 | — | Free | Weather, battery… later. Slot 2 was the thick selected arc until 2026-10-27 (R9) |

**Why widths travel in MAX:** WFF can't measure text, and one line can't mix colors (a second `Font` run is ignored), so icons can't be inline glyphs. The data source measures each line with the watch's own default typeface at the face's sizes, shortens it with an ellipsis (C9) and sends the width; the face draws the icon as shapes at `225 − (width + icon space) / 2` and nudges the text right by half the icon space. A slot's bounding area also crops its drawing, so text slots use a full-face bounding oval.

Face-side, no slot: comet trail (240 dashes lit by time expressions) and the now dot (R6), verified in spike 3.

Why one image (spike 2, PRD §6.3b): `WEIGHTED_ELEMENTS` is capped at 7 segments (3 meetings), and `PHOTO_IMAGE` is rejected for third-party providers. Free time in any `WEIGHTED_ELEMENTS` slot must be `#10000000`, not fully transparent (`#0` breaks the runtime). `CUBIC_BEZIER` animation `controls` must be space-separated (`"0.4 0 0.2 1"`); commas freeze the renderer (spike 3). `[SECOND_MILLISECOND]` is seconds with a fraction (e.g. `44.543`), not milliseconds. Any slot whose drawing reads it makes the face redraw ~10×/s while that slot has data (~8× CPU with the screen on), so such slots send no data unless they're animating (spike 5).

Private titles/places are never in any slot: `details` carries "Private" / "Details hidden", and the ring image only has grey stripes.

Taps: 12 `Launch` hotspots on the band → `app://io.github.karthikeyags07.scheduleclock/select?hour=N` (verified in spike 1: ~0.2–0.5 s to the face), plus 12 thin hotspots on the later lane → `…&lane=later` (I11), and a details hotspot (I8, x 105–345, y 205–335, inside the hour hotspots) → `…/release` (back to the normal view) → the data source's invisible `TapActivity` updates the selection. Feasibility risks are tracked in PRD §6.3, results in §6.3a–b.

---

## 9. Rule changes

| Date | Change | Rules |
|---|---|---|
| 2026-10-28 | Time-Shift on the watch, as a Style option (time-zone location only). | §10 |
| 2026-10-28 | Colour themes on the watch: Amber, Mint, Violet, Rose in the customize screen (Colours). | §9 |
| 2026-10-28 | Meeting buzzes: 2 min before start, halfway, 5 min before end (watch only). | §11 H1–H5 |
| 2026-10-27 | Tapping the details while a meeting is selected goes back to the normal view (prototype and watch). | I8, §8 taps |
| 2026-10-27 | A tomorrow meeting with no place reads "Tomorrow", not "Tomorrow ·". | C6 |
| 2026-10-27 | Watch: C3 empty state built (status flag 32): with no meetings the date and clock move to the center, clock 115 %, divider fades (M4). It was missing on the watch, which kept the clock up top. No rule change. | §8 slot 4, C3, M4 |
| 2026-10-27 | Watch: C9's 30 → 24 px title step built (status flag 16); before, a title that fit only at 24 px got an ellipsis. No rule change. | §8 slot 4 |
| 2026-10-27 | Night meetings before 6 AM are held on the lane too: nothing is on the main ring before 6 AM. During the hold the lane reaches the 12 h window (the 14 h look-ahead doesn't apply). | W5a, W5 |
| 2026-10-27 | From 12 to 6 AM the day's meetings (6 AM on) stay on the later lane, shown once they fit, and all move to the main ring at 6 AM. Meetings before 6 AM are unchanged. | W5a, W5 |
| 2026-10-27 | Selecting a meeting no longer thickens its arc: it keeps its normal width and colours at full strength, and everything else dims to 35 %. On the watch the ring image draws this, and the selected-arc slot is removed (6 of 8 slots used). | R9, R10, R1a, M1, §8 |
| 2026-10-25 | Watch build gains the date, time range, place line, look-ahead arrow, lock and overlap line (C1, C4, C7, C12, C13), driven by a first port of the rules engine; `details` becomes a RANGED_VALUE slot with packed width/kind. | §8 slots 3–4 |
| 2026-10-25 | Selected arc no longer slides between meetings (it swept across the face): angles jump, the old arc fades out in place and the new one fades in. The ring image only dims the others; the highlight comes from the arc. | §8 slot 2, R9, M2 |
| 2026-10-25 | Spike 3: selected and settle slots are `RANGED_VALUE` (angles in MIN/MAX, state in VALUE); trail and now dot are face-side. | §8, R6, R9, M3 |
| 2026-10-25 | Slot plan after spikes 1–2: the ring is one image (slot 1), clock/date/details live inside the status slot, 3 slots freed. | §8 |
| 2026-10-24 | Precise taps on the inner lane select the later meeting directly; 7 AM + 7 PM stacking documented. | I3, I11, §8 |
| 2026-10-24 | Private stripes recolored from amber to grey. | R10, color tokens |
| 2026-10-24 | Private meetings: caution-stripe arcs, "Private" + lock, "Details hidden", center tap opens the calendar. All 8 slots used. | P10, R10, C13, I8, A1, §8 |
| 2026-10-24 | NEXT/NOW label replaced by an amber arrow before the next meeting's title. | C12, T2, M1, A2 |
| 2026-10-24 | During a live meeting the details look ahead to the next meeting, with a NEXT indicator (NOW when a live meeting is shown). | C1, C12, T2, M1, A2 |
| 2026-10-24 | Dial numbers 1–12 also in Anta. | R8, T5 |
| 2026-10-24 | Clock font switched to **Anta** (Google Fonts, open license) at 88 px; empty-state 115 % enlargement restored. Monument Extended dropped (license). | T1, T5, C3, M4 |
| 2026-10-24 | Clock digits in Monument Extended at 70 px (was system sans 88 px); time + AM/PM centered together; empty state no longer enlarges the clock. | T1, T5, C3, C11, M4 |
| 2026-10-24 | Ring moved flush to the screen edge (R 206 → 219); selected arc grows inward; lane, ticks and numbers move out with it. | R1, R4, R9 |
| 2026-10-24 | Empty state shows only date + clock; "Nothing else today" removed. | C3, M4 |
| 2026-10-24 | Selection follows the meeting itself, not its list position: it survives a lane change and releases when the meeting is no longer shown. | I10, W10 |
| 2026-10-24 | Tomorrow's meetings inside the 12 h window are shown (later: 14 h look-ahead toggle, W6). "Tomorrow ·" goes on the place line. | W5, W6, C6 |
| 2026-10-24 | Tomorrow's meetings stay on the lane until midnight, then move to the ring (W5). |
| 2026-10-24 | Later-lane meetings move to the main ring only when they fit completely, with an animation. | W8, M3 |
| 2026-10-24 | Later-today lane and double-booking stripes introduced. | W4, R4, R5, C5, C7 |
| 2026-10-24 | 24 px minimum text; time and place split onto two lines. | T1, C4 |
| 2026-10-24 | Status line hidden. | C10 |
| 2026-10-24 | 15-min ticks, hour numbers, comet trail. | R6–R8 |
| 2026-10-24 | Scale-only select/release ("one layout, two sizes"). | P2, M1, M2 |
| 2026-10-24 | One-hour tap sections. | I1–I7 |

## 9. Colour themes

Every colour the face draws with is a token (the table in §2: meeting arcs, elapsed part, later lane, later/tomorrow text, double booking and its elapsed part, private stripes, clock/title text, secondary text, ring track). A **theme** is one complete set of tokens. The prototype's **Colors** section lists the themes, lets you edit any token with a colour picker (this becomes the "Custom" theme, saved in the browser) and **Copy theme** exports the tokens as JSON. Amber is the real design; Mint, Violet and Rose are examples of the mechanism, to be replaced with your colours. Ambient mode stays monochrome whatever the theme (A1), and Dim and Tactical red (A4, A5) apply on top of a theme.

**On the watch (built 2026-10-28):** the four themes are one **Colours** option (WFF `ColorConfiguration`) in the watch's customize screen; each carries three colours: meeting arcs, later lane, later/tomorrow text. The watch app can't see the chosen theme, so the ring image is three 450 px panels side by side and the face draws it three times, shifted so one panel is on screen each time. The face multiplies each copy by a tint: panel 0 (meeting arcs in white, elapsed parts at 45 % grey) × the arc colour, panel 1 (the later lane in white) × the lane colour, panel 2 untinted (private stripes, double bookings, coral lane). The look-ahead arrow, the settle animation and the "Tomorrow" text use the theme colours directly. Differences from the prototype: the elapsed shade is always 45 % of the arc colour (the prototype's are within a few points of that); double bookings stay coral in every theme (Violet's pink-coral isn't used); there is no Custom theme (the customize screen offers a fixed list).

## 10. Time-Shift face

A second face, picked in the prototype's **Face** control (Calen-Time / Time-Shift). Everything else (rules, layout, motion) is the same as Calen-Time; only the colour of the main-ring meetings changes.

| # | When | What | Why |
|---|---|---|---|
| S1 | Every day | **Sunrise and sunset** are computed on the device for the user's location and the day shown (the standard solar formula, accurate to about a minute; no network). Location: the user's position once they allow it (rounded to 0.01°, about 1 km, and kept on the device), otherwise a guess from the time zone (India → central India). Recomputed when the day changes. | The colours follow the real light where the user is. Computing locally works offline and on the watch. |
| S2 | Time-Shift, not ambient | Each **main-ring meeting** (and the selected arc) is coloured by the light at its minutes, so an arc spanning sunset grades through it. The colour wheel for the visible 12 h sits under the ring and shows **only through the meeting arcs**; free time stays dark. The arcs stay solid (an opaque backing under the gradient, R6a) at the arc brightness setting; elapsed parts are dimmer. | One standard colour for every minute of the day; meetings reveal it. |
| S3 | The day's colours | Anchored to sunrise (SR), solar noon and sunset (SS): deep night blue `#3550c8` · SR −80 min pre-dawn blue `#4a5fd0` · SR −40 dawn violet `#8b6fd6` · SR −12 dawn rose `#e58ab0` · SR +15 sunrise apricot `#ffa86b` · SR +80 morning gold `#ffd27a` · noon pale gold `#ffe7a3` · SS −90 afternoon gold `#ffbf5a` · SS −25 golden-hour orange `#ff9447` · SS sunset coral `#ff6a5a` · SS +20 dusk pink `#e0608f` · SS +45 twilight violet `#9a68d8` · SS +85 night blue `#4a5fd0` · midnight `#3550c8`. Blended in OKLab so mixes stay vivid. Polar day: pale gold all day; polar night: night blue. | Example: a 5–6 PM meeting with sunset at 5:43 runs golden orange → sunset coral → dusk pink. |
| S4 | Unchanged by Time-Shift | Private meetings keep their grey caution stripes (R10); double bookings and all-day clashes keep their coral stripes on top (R4, W9); the later lane stays blue (R5); ambient is outlines only (A1); Dim and Tactical red apply on top (A4, A5). | Meaning beats mood: those markings carry information. |

**On the watch (built 2026-10-28):** Time-Shift is a **Style** option in the watch's customize screen (Calen-Time / Time-Shift), next to Colours; the Colours theme still sets the lane, arrow and text. The watch app computes the sun times and colours the same way (`SunColors`) and draws the Time-Shift arcs into a fourth, untinted panel of the ring image through a colour sweep around the dial (one colour per degree for the time it shows in the visible 12 h). The style picks panel 3 instead of the tinted panel 0. Elapsed parts are 45 % of the colour, dimmed meetings 35 % (R9). Location: the time-zone guess only for now (central India for India); the watch's own location needs a location permission, not added yet.

## 11. Meeting buzzes (haptics)

The watch vibrates at three moments of every meeting from the real calendar, screen on or off. Built in the watch's data source app (not the face): one exact alarm for the next buzz, re-armed when the calendar changes, every 5 min with the ring, after a restart and after a clock or time-zone change. The prototype doesn't simulate them.

| # | When | What | Why |
|---|---|---|---|
| H1 | **2 min before** a meeting starts | **One sharp buzz, 0.75 s**, full strength. | Time to wrap up and join. |
| H2 | **5 min before** a meeting ends | **Two buzzes, 0.75 s each**, 0.4 s apart. | Time to wrap up; the double buzz tells it apart from the start. |
| H3 | **Halfway** through a meeting | **One long buzz, 1.5 s**. | A pacing check, felt as different from both. |
| H4 | Short meetings | No halfway buzz under **15 min**; no end warning under **10 min** (they would land on top of the others). All-day events never buzz; nor do the sample days. Declined and cancelled events aren't in the calendar data at all. | Each buzz should mean one thing. |
| H5 | Several buzzes fall at the same moment (back-to-back or double-booked meetings) | Only the most important plays: end warning, then start, then halfway. A buzz delayed by up to 2 min (watch asleep) still plays; later than that it's skipped. | One clear signal, never a pile-up. |

Vibrations use the alarm channel, so they play with the screen off and **even in Do Not Disturb** (decided 2026-10-28: people often turn DND on during meetings, which is when the end warning matters). Debug: `app://io.github.karthikeyags07.scheduleclock/buzz?kind=start|middle|end` plays a pattern now.

