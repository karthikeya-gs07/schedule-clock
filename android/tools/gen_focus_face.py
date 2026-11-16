"""The Focus layer (RULES A26): part of every colourway's main face, shown over it while the phone's focus mode is on.

Used by tools/gen_spike_face.py: focus_slot(...) returns the `focus` complication slot, in the colourway's colours
(its dial, ring track, text and accent), and write_theme(...) adds those colours to prototype/focus-themes.js for the
phone's preview. Focus mode used to be a face of its own per colourway (24 extra faces, swapped in and out); as a slot
in the main face it switches in about a second, with no face swap.

The slot, `focus` (FocusDataSourceService), RANGED_VALUE; no data (nothing drawn) while focus mode is off:
  VALUE = the moment counted down to, in seconds from today's midnight (the meeting's end while one is on, else
          the next one's start; tomorrow's go past 86400).
  MIN   = where the ring starts: the meeting's start while one is on (the ring empties as it runs), else an hour
          before the next one (full an hour out, empty as it starts).
  MAX   = 300000 + flags: 1 a meeting is on, 2 one is next (neither: nothing left today or tomorrow),
          4 24-hour clock, 8 hide the meeting in always-on (A2c).
  TEXT  = the meeting's title ("Private" for a private one); TITLE = its time range.
While it has data it covers the whole face (the colourway's dial; black in always-on) and draws the Focus layout:
the time small at the top, "Ends in" / "Starts in", the countdown counted by the face every second from the watch's
clock (mm:ss, or h:mm:ss), the meeting's title and time, and a ring round the edge that empties as it runs down.
Always-on: no seconds and no ring: the time large, "Ends in 24 min", and the title (unless hidden in always-on).
"""
import json
from pathlib import Path

PKG = "io.github.karthikeyags07.scheduleclock"
FONT = "anta_regular"
CX = CY = 225
R = 219
AOD_TEXT, AOD_MUTED = "#ffc8c8cc", "#ff7a7a80"   # always-on: greys on black, whatever the colourway
BG = TRACK = TEXT = MUTED = ACCENT = None         # set by focus_slot()


def write_theme(js: Path, colourway, bg, track, text_, muted, accent):
    """Adds [colourway]'s Focus colours to the phone preview's table (prototype/focus-themes.js)."""
    table = json.loads(js.read_text().split("=", 1)[1].rstrip().rstrip(";")) if js.exists() else {}
    table[colourway] = {k: "#" + v[3:] for k, v in dict(bg=bg, track=track, text=text_, muted=muted, accent=accent).items()}
    js.write_text("// Written by android/tools/gen_spike_face.py: each colourway's Focus colours (RULES A26).\n"
                  "const FOCUS_THEMES = " + json.dumps(dict(sorted(table.items())), indent=1) + ";\n")

V, LO = "[COMPLICATION.RANGED_VALUE_VALUE]", "[COMPLICATION.RANGED_VALUE_MIN]"
F = "([COMPLICATION.RANGED_VALUE_MAX] - 300000)"
LIVE = f"({F} % 2)"
NEXT = f"(floor({F} / 2) % 2)"
NONE = f"(1 - {LIVE} - {NEXT})"
H24 = f"(floor({F} / 4) % 2)"
HIDEAOD = f"(floor({F} / 8) % 2)"
NOW = "([HOUR_0_23] * 3600 + [MINUTE] * 60 + [SECOND])"
LEFT = f"clamp({V} - {NOW}, 0, 200000)"
HRS = f"floor({LEFT} / 3600)"
MINS = f"floor(({LEFT} % 3600) / 60)"
SECS = f"floor({LEFT} % 60)"
FRAC = f"clamp({LEFT} / ({V} - {LO} + 0.001), 0, 1)"
AOD_MIN = f"floor(({LEFT} + 59) / 60)"   # always-on: minutes left, rounded up


def text(x, y, w, h, size, color, template, *exprs, weight="NORMAL", family="SYNC_TO_DEVICE", upper=False, spacing="", cut=True):
    params = "".join(f'<Parameter expression="{e}"/>' for e in exprs)
    body = f"<Template><![CDATA[{template}]]>{params}</Template>" if exprs else template   # fixed text goes in as it is
    if upper: body = f"<Upper>{body}</Upper>"
    sp = f' letterSpacing="{spacing}"' if spacing else ""
    return (f'<PartText x="{x}" y="{y}" width="{w}" height="{h}"><Text align="CENTER" ellipsis="{"TRUE" if cut else "FALSE"}">'
            f'<Font family="{family}" size="{size}" weight="{weight}" color="{color}"{sp}>{body}</Font></Text></PartText>')


def clock(y, h, size, color, align="CENTER"):
    """The time, 12- or 24-hour as the phone says."""
    out = []
    for fmt, hf, show in (("h:mm", "12", f"(1 - {H24})"), ("h:mm", "24", H24)):
        out.append(f'''        <Group name="clock_{hf}" x="0" y="{y}" width="450" height="{h}">
          <Transform target="alpha" value="255 * {show}"/>
          <DigitalClock x="0" y="0" width="450" height="{h}">
            <TimeText x="0" y="0" width="450" height="{h}" format="{fmt}" hourFormat="{hf}" align="{align}">
              <Font family="{FONT}" size="{size}" color="{color}"/>
            </TimeText>
          </DigitalClock>
        </Group>''')
    return "\n".join(out)


def countdown(y):
    """The time left, each digit in its own fixed box so it doesn't shuffle as it ticks (Anta's digits differ in width)."""
    out = []
    # (show when, size, digit width, colon width, cells): a cell is an expression for one digit, or ":".
    tens = lambda e: f"floor({e} / 10)"
    ones = lambda e: f"floor({e} % 10)"
    variants = [
        (f"({HRS} == 0)", 104, 66, 26, [tens(MINS), ones(MINS), ":", tens(SECS), ones(SECS)]),
        (f"({HRS} &gt; 0) * ({HRS} &lt; 10)", 80, 51, 20, [HRS, ":", tens(MINS), ones(MINS), ":", tens(SECS), ones(SECS)]),
        (f"({HRS} &gt;= 10)", 72, 46, 18, [tens(HRS), ones(HRS), ":", tens(MINS), ones(MINS), ":", tens(SECS), ones(SECS)]),
    ]
    for n, (when, size, dw, cw, cells) in enumerate(variants):
        total = sum(cw if c == ":" else dw for c in cells)
        x = (450 - total) / 2
        parts = []
        for c in cells:
            w = cw if c == ":" else dw
            # Each box is wider than its step (a digit is wider than the step), centred on it, so nothing is cut.
            box = round(size * .9); left = round(x + w / 2 - box / 2)
            if c == ":":
                parts.append(text(left, 0, box, 120, size, TEXT, ":", family=FONT, cut=False))
            else:
                parts.append(text(left, 0, box, 120, size, TEXT, "%d", c, family=FONT, cut=False))
            x += w
        out.append(f'''        <Group name="left_{n}" x="0" y="{y}" width="450" height="120">
          <Transform target="alpha" value="255 * {when}"/>
          {chr(10).join("          " + p for p in parts).strip()}
        </Group>''')
    return "\n".join(out)


def focus_slot(bg, track, text_, muted, accent, slot_id=8):
    """The `focus` slot in these colours, to go over everything else in the main face (under its tap spots)."""
    global BG, TRACK, TEXT, MUTED, ACCENT
    BG, TRACK, TEXT, MUTED, ACCENT = bg, track, text_, muted, accent
    return f'''
    <!-- Focus mode (A26): with data, covers the face and shows the countdown; no data while focus mode is off. -->
    <ComplicationSlot slotId="{slot_id}" name="focus" displayName="@string/slot_focus" x="0" y="0" width="450" height="450"
        supportedTypes="RANGED_VALUE EMPTY" isCustomizable="FALSE">
      <DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY"
          primaryProvider="{PKG}/{PKG}.FocusDataSourceService" primaryProviderType="RANGED_VALUE"/>
      <BoundingOval x="0" y="0" width="450" height="450"/>
      <Complication type="RANGED_VALUE">
        <!-- The cover: the colourway's dial over the main face, black in always-on -->
        <PartDraw x="0" y="0" width="450" height="450">
          <Rectangle x="0" y="0" width="450" height="450"><Fill color="{BG}"/></Rectangle>
        </PartDraw>
        <PartDraw x="0" y="0" width="450" height="450" alpha="0">
          <Variant mode="AMBIENT" target="alpha" value="255" duration="0.5"/>
          <Rectangle x="0" y="0" width="450" height="450"><Fill color="#ff000000"/></Rectangle>
        </PartDraw>

        <!-- Interactive -->
        <Group name="active" x="0" y="0" width="450" height="450">
          <Variant mode="AMBIENT" target="alpha" value="0" duration="0.5"/>
          <!-- The ring: the time left, emptying clockwise from 12 -->
          <PartDraw x="0" y="0" width="450" height="450">
            <Arc centerX="{CX}" centerY="{CY}" width="{2*R}" height="{2*R}" startAngle="0" endAngle="360">
              <Stroke color="{TRACK}" thickness="12"/>
            </Arc>
          </PartDraw>
          <PartDraw x="0" y="0" width="450" height="450">
            <Transform target="alpha" value="255 * (1 - {NONE}) * ({FRAC} &gt; 0)"/>
            <Arc centerX="{CX}" centerY="{CY}" width="{2*R}" height="{2*R}" startAngle="0" endAngle="1">
              <Transform target="endAngle" value="clamp(360 * {FRAC}, 1, 360)"/>
              <Stroke color="{ACCENT}" thickness="12" cap="ROUND"/>
            </Arc>
          </PartDraw>

          <!-- A meeting on or next -->
          <Group name="meeting" x="0" y="0" width="450" height="450">
            <Transform target="alpha" value="255 * (1 - {NONE})"/>
{clock(78, 50, 40, MUTED)}
            <Group name="label_live" x="0" y="138" width="450" height="32">
              <Transform target="alpha" value="255 * {LIVE}"/>
              {text(0, 0, 450, 32, 24, ACCENT, "Ends in", weight="MEDIUM", upper=True, spacing="0.08")}
            </Group>
            <Group name="label_next" x="0" y="138" width="450" height="32">
              <Transform target="alpha" value="255 * {NEXT}"/>
              {text(0, 0, 450, 32, 24, ACCENT, "Starts in", weight="MEDIUM", upper=True, spacing="0.08")}
            </Group>
{countdown(168)}
            <Group name="title" x="0" y="296" width="450" height="40">
              {text(70, 0, 310, 40, 30, TEXT, "%s", "[COMPLICATION.TEXT]", weight="MEDIUM")}
            </Group>
            <Group name="range" x="0" y="338" width="450" height="30">
              {text(90, 0, 270, 30, 22, MUTED, "%s", "[COMPLICATION.TITLE]")}
            </Group>
          </Group>

          <!-- Nothing left today or tomorrow -->
          <Group name="idle" x="0" y="0" width="450" height="450">
            <Transform target="alpha" value="255 * {NONE}"/>
{clock(155, 110, 96, TEXT)}
            {text(0, 280, 450, 32, 24, MUTED, "No more meetings")}
          </Group>
        </Group>

        <!-- Always-on: once a minute, so no seconds and no ring -->
        <Group name="ambient" x="0" y="0" width="450" height="450" alpha="0">
          <Variant mode="AMBIENT" target="alpha" value="255" duration="0.5"/>
{clock(118, 110, 96, AOD_TEXT)}
          <Group name="aod_meeting" x="0" y="0" width="450" height="450">
            <Transform target="alpha" value="255 * (1 - {NONE})"/>
            <Group name="aod_live_m" x="0" y="246" width="450" height="32">
              <Transform target="alpha" value="255 * {LIVE} * ({AOD_MIN} &lt; 60)"/>
              {text(0, 0, 450, 32, 24, AOD_MUTED, "Ends in %d min", AOD_MIN)}
            </Group>
            <Group name="aod_live_h" x="0" y="246" width="450" height="32">
              <Transform target="alpha" value="255 * {LIVE} * ({AOD_MIN} &gt;= 60)"/>
              {text(0, 0, 450, 32, 24, AOD_MUTED, "Ends in %d h %d min", f"floor({AOD_MIN} / 60)", f"floor({AOD_MIN} % 60)")}
            </Group>
            <Group name="aod_next_m" x="0" y="246" width="450" height="32">
              <Transform target="alpha" value="255 * {NEXT} * ({AOD_MIN} &lt; 60)"/>
              {text(0, 0, 450, 32, 24, AOD_MUTED, "Starts in %d min", AOD_MIN)}
            </Group>
            <Group name="aod_next_h" x="0" y="246" width="450" height="32">
              <Transform target="alpha" value="255 * {NEXT} * ({AOD_MIN} &gt;= 60)"/>
              {text(0, 0, 450, 32, 24, AOD_MUTED, "Starts in %d h %d min", f"floor({AOD_MIN} / 60)", f"floor({AOD_MIN} % 60)")}
            </Group>
            <Group name="aod_title" x="0" y="286" width="450" height="40">
              <Transform target="alpha" value="255 * (1 - {HIDEAOD})"/>
              {text(70, 0, 310, 40, 28, AOD_TEXT, "%s", "[COMPLICATION.TEXT]")}
            </Group>
          </Group>
          <Group name="aod_idle" x="0" y="246" width="450" height="32">
            <Transform target="alpha" value="255 * {NONE}"/>
            {text(0, 0, 450, 32, 24, AOD_MUTED, "No more meetings")}
          </Group>
        </Group>
      </Complication>
    </ComplicationSlot>
'''
