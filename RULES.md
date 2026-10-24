# Schedule Clock: Behavior & Design Rules

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
| P4 | **One question per visual channel** | **Lane / radius** = when (soon vs later) · **thickness** = emphasis (selected) · **color** = state (amber normal, coral clash, blue later, dim = elapsed) · **shape** (split band) = double booking. A channel never means two things. |
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
| W5 | A meeting is **tomorrow** and starts within the window | It ends within the window → main ring. It ends past the window → later lane. | The user asked to see tomorrow's meetings that fall inside the visible 12 hours. |
| W6 | A meeting is tomorrow and starts **after** the window, or is further out | **Not shown.** | Keeps the face to the visible 12 hours (P8). |
| W7 | A meeting has ended | Not shown. | Past is noise on a glanceable face. |
| W8 | A later-lane meeting comes to **fit completely** in the window (end ≤ window end) | It **moves to the main ring**, animated (M3). Example: the 7–8 PM call moves at **8:00 AM**, after the now dot and trail have passed the 7→8 slot. | Never shows as a clipped sliver, and never shares a slot with now (P6). |
| W9 | All-day events | Ignored (v1). | No position on a time dial. Open question Q5. |
| W10 | List order (for "next" and for taps) | **Chronological across both lanes.** | Indexes don't shift when a meeting changes lane, so a selection never jumps. |

---

## 2. Ring drawing

| # | When | Then | Why |
|---|---|---|---|
| R1 | Always | **Dial:** 0° = 12 o'clock, clockwise, 12 hours per turn. **Track:** 12 px, `#1c1c1f`, radius **R = 219**, so the band (213–225) is **flush with the screen edge**. | Familiar clock geometry (P3). Using the very edge gives the center content the most room and makes the ring read as the watch's own bezel. |
| R2 | Two meetings are back to back | **1° gap** at each arc end. | Adjacent meetings read as separate (WFF: `discreteGap`). |
| R3 | A meeting is in progress | **Elapsed** part (start → now) is dim amber `#6e4f22`; the **remaining** part is amber `#ffb547`; the now dot sits between them. | Shows how far into the meeting you are. |
| R4 | Two or more main-ring meetings **overlap** | Only the **overlapping minutes** become **two 5 px coral stripes** (`#ff7a59`) at R ± 4.5, with a 4 px black gap, inside the normal 12 px band (the outer stripe's last ~1 px falls past the screen edge). Elapsed overlap is dim coral `#7a3b2c`. The non-overlapping parts stay amber. 3+ overlaps still use two stripes. | Shape (split = "two things here") plus color (coral = attention) (P4, P5). It stays in the band's footprint, so it never reads as the later lane. |
| R5 | Later-lane meeting (W4/W5) | Arc at **R − 10**, **4 px**, `#8fa3d6`, rounded ends. | Thin, cool and inside reads as "further away"; thick, warm and outside reads as "soon" (P4). |
| R6 | Always (not ambient) | **Now marker:** white dot with a soft halo on the main ring. **Comet trail:** 55° behind it, fading from 60 % white to transparent, drawn **under** meeting arcs. | Shows time sweeping through the day; the trail only shows over free time. |
| R7 | Always | **Ticks** every 15 min, inside the later lane: hour (10 px, `#8e8e93`, 2.5 px wide), half-hour (6 px, `#4a4a50`), quarter (3 px, `#33333a`). | Three marks between each pair of hours for fine reading, faint enough not to compete. |
| R8 | Always | **Hour numbers** 1–12, 24 px **Anta** (T5), radius R − 40. 12/3/6/9 `#8e8e93`, others `#5c5c62`. | Readable at the 24 px minimum (P7); cardinal hours stand out for orientation. |
| R10 | A meeting is marked **private** in the calendar | Its arc is a **grey caution band**: 5 px `#8e8e93` stripes / 5 px gaps at **45° to the arc**, instead of solid amber (elapsed part: `#48484c` stripes). Same position and thickness as any meeting, so free/busy stays readable. On the later lane: grey dashed (4 on / 3 off). When selected: 22 px band of `#c7c7cc` stripes (elapsed `#6a6a70`; lane: grey dashed 8 px). Ambient: grey stripes. | Shows "busy, but not for display" at a glance, without color alone (P5): the stripe pattern is the cue. Grey (not amber) keeps private time quiet and visually separate from meetings you can act on (changed 2026-10-24). |
| R9 | A meeting is selected | Selected arc: main ring → **22 px** `#ffd27f` (elapsed part `#8a6a35`), centered at R − 5 so it **grows inward** (203–225) and isn't cut off by the screen edge; later lane → **8 px** `#c3d0f2`. All other arcs → **35 % opacity**. | Thickness = emphasis (P4). Dimming the rest isolates the selection. |

**Color tokens**

| Token | Hex | Use |
|---|---|---|
| event | `#ffb547` | Main-ring meeting |
| past | `#6e4f22` | Elapsed part of a live meeting |
| selected / selectedPast | `#ffd27f` / `#8a6a35` | Selected main-ring arc |
| later / laterSelected | `#8fa3d6` / `#c3d0f2` | Later lane |
| laterText | `#a9b8e0` | "Later ·" and "Tomorrow ·" text |
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
| C5 | The meeting is on the later lane and is **today** | Time line: **"Later · 7:00 – 8:00 PM"** in `#a9b8e0`. | Matches the blue lane; explains why it's not on the main ring. |
| C6 | The meeting is **tomorrow** | Place line: **"Tomorrow · Café Blue"** in `#a9b8e0`. | "Tomorrow · 8:00 – 8:45 AM" doesn't fit the time line at 24 px, so the qualifier goes on the place line. |
| C7 | The meeting is **double-booked** | Place line: a coral **overlap icon** (two intersecting rings, 22 px) followed by the other meeting's name, **"Vendor call"** (or "Vendor call +1" for 3+), in coral; no "Also" word. Takes priority over C6 and the place. | Who you're clashing with matters more than where. |
| C8 | Time range formatting | AM/PM appears once if both ends are in the same half of the day ("1:00 – 2:00 PM"), otherwise on both ("11:30 AM – 12:30 PM"). | Saves width. |
| C9 | Text too wide | Title: shrink 30 → 24 px, then ellipsis. Time and place: ellipsis at 24 px. Max widths: title 260, time 255, place 220. | Keeps text clear of the hour numbers in both default and selected (×1.15) states. |
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
| I8 | You tap the center | If the shown meeting is **private**: open the **calendar app** (`Launch target="CALENDAR"`) so details are read there, behind the watch's lock. Otherwise nothing. | P10: the face never reveals private details; the app is the only way in. |
| I9 | Ambient mode | Taps do nothing; no selection. | |
| I10 | The selected meeting disappears (ended, or day/time changed) | Back to default. | |
| I11 | You tap **precisely on the thin later lane** (from 6 px inside it to 2 px outside, radius 203–211) in an hour that has a later-lane meeting | Only that hour's **later-lane meetings** are considered: the first tap selects the evening meeting directly (skipping the morning one); tapping it again goes back to default. A tap anywhere else in the band follows I2–I5. | A shortcut for people who can hit it; the double tap (I3) stays the reliable path. The lane zone never extends into the main band, so normal taps aren't stolen. On the watch: 12 extra thin hotspots → `select?hour=N&lane=later`. |

---

## 6. Motion

| # | When | Then |
|---|---|---|
| M1 | **Select / release** (360 ms, `cubic-bezier(.2,.8,.2,1)`; release = reverse) | Time → 55 %, up 10 px · Date → 90 %, stays put (moving it would hit the 11 and 1) · Divider up 32 px, 60 % wide · Details (incl. the C12 arrow) → 115 %, up 30 px · Selected arc 12 → 22 px and fades in · Other arcs → 35 %. |
| M2 | The **shown meeting changes** (selecting a meeting other than "next", or switching A → B) | Text dips to 0 opacity for **140 ms**, the content swaps, and it fades back. The layout keeps scaling, with no second screen (P2). Selecting the meeting that's already shown doesn't dip. |
| M3 | **Lane → main ring** (W8) | The blue arc grows outward from R − 10 to R about the center, 4 → 12 px, blue → amber, **700 ms** with a slight overshoot (`cubic-bezier(.3,1.35,.5,1)`), so it "sits down" into the band. |
| M4 | Meetings ↔ **empty** (C3) | Date and clock move down to the center (clock 115 %) and the divider fades, with the M1 timing. |
| M5 | System **reduced motion** is on | All of the above are instant. |
| M6 | Prototype review aids | **Slow motion ×5** (M1 1.8 s, M2 0.7 s, M3 3.5 s) and **Fast-forward time** (1 simulated min / 40 ms). |

---

## 7. Ambient (always-on)

| # | Rule | Why |
|---|---|---|
| A1 | Black background. Meeting arcs become **3 px grey** lines; elapsed `#4a4a4a`; later lane `#5c5c62`; a double booking shows as two grey lines; a private meeting shows as grey stripes. | Low power, low burn-in. Structure survives without color. |
| A2 | Trail hidden; ticks and numbers at 60 %; time range and place hidden; title stays; the C12 arrow turns grey. | Minimum lit pixels while still showing what's next. |
| A3 | No selection; taps ignored. | |

---

## 8. Data → face mapping (Watch Face Format)

| Slot | Type | Carries | Drawn as |
|---|---|---|---|
| 1 `ring` | WEIGHTED_ELEMENTS | Main-ring meetings (amber / dim amber) | `Arc` + `WeightedStroke`, 12 px |
| 2 `ring_selected` | WEIGHTED_ELEMENTS | Selected meeting only | Same arc, 22 px, on top |
| 3 `details` | LONG_TEXT | Title, time range, place | Center text |
| 4 `status` | RANGED_VALUE | VALUE = `selected` (0/1); TEXT = status line | Drives every M1 `Transform` |
| 5 `ring_later` | WEIGHTED_ELEMENTS | Later-lane meetings | Thin arc at R − 10 |
| 6 `ring_clash` | WEIGHTED_ELEMENTS | Overlap ranges only | Drawn 3 times: black mask + 2 coral stripes |
| 7 `ring_promote` | WEIGHTED_ELEMENTS | The promoting meeting, for ~1 s | Animated M3 arc |
| 8 `ring_private` | WEIGHTED_ELEMENTS | Private main-ring meetings only (removed from `ring`) | Caution-stripe arc (R10) |

**All 8 slots are now used.** Private titles/places are never in any slot: `details` carries "Private" / "Details hidden".

Taps: 12 `Launch` hotspots on the band → `app://scheduleclock/select?hour=N`, plus 12 thin hotspots on the later lane → `…&lane=later` (I11), and a center hotspot (I8) → the data source app updates the selection. Feasibility risks for each row are tracked in PRD §6.4.

---

## 9. Rule changes

| Date | Change | Rules |
|---|---|---|
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
| 2026-10-24 | Tomorrow's meetings inside the 12 h window are shown; nothing beyond it. "Tomorrow ·" goes on the place line. | W5, W6, C6 |
| 2026-10-24 | Later-lane meetings move to the main ring only when they fit completely, with an animation. | W8, M3 |
| 2026-10-24 | Later-today lane and double-booking stripes introduced. | W4, R4, R5, C5, C7 |
| 2026-10-24 | 24 px minimum text; time and place split onto two lines. | T1, C4 |
| 2026-10-24 | Status line hidden. | C10 |
| 2026-10-24 | 15-min ticks, hour numbers, comet trail. | R6–R8 |
| 2026-10-24 | Scale-only select/release ("one layout, two sizes"). | P2, M1, M2 |
| 2026-10-24 | One-hour tap sections. | I1–I7 |
