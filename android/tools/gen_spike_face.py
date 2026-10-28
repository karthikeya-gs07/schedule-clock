"""Generates the Phase 1 spike watchface.xml and a preview PNG.

Usage: python3 tools/gen_spike_face.py watchface/src/main/res
Layers, bottom to top: track, comet trail, ticks + numbers, ring image with the selection (slot 0), settling arc
(slot 5), now dot, date/clock/title/range (status slot 1), place line (slot 3), tap hotspots. Comments name the
PRD §6.3 risk each block tests; slot numbers follow RULES §8.
"""
import math, struct, sys, zlib
from pathlib import Path

res = Path(sys.argv[1])
PKG = "io.github.karthikeyags07.scheduleclock"
CX = CY = 225
R = 219          # ring radius: band 213–225, flush with the edge (RULES R1)
R_LATER = R - 10 # later lane (R5)
HOT = 72         # hotspot box size

SEAM = 294   # right edge of a 1-digit-hour time, so time + AM/PM are centered as a unit (C11)
SELECT_ANIM = '<Animation duration="0.52" interpolation="CUBIC_BEZIER" controls="0.4 0 0.2 1"/>'   # M1
SETTLE_ANIM = '<Animation duration="0.9" interpolation="CUBIC_BEZIER" controls="0.22 1 0.36 1"/>'  # M3

def provider(cls, typ):
    return (f'<DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY"\n'
            f'          primaryProvider="{PKG}/{PKG}.{cls}" primaryProviderType="{typ}"/>')

# Progress 0..1 carried by a RANGED_VALUE as VALUE between MIN and MAX (slots 2 and 5).
P = ("clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
     "([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1)")

# --- Comet trail (R6): 240 fixed dashes across the band, lit by how recently "now" passed them.
NOW_DEG = "(([HOUR_0_11] * 60 + [MINUTE]) * 0.5)"
TRAIL_DEG = 55

def dash(i):
    deg = i * 1.5
    a = math.radians(deg)
    p1 = (CX + 214 * math.sin(a), CY - 214 * math.cos(a))
    p2 = (CX + 224 * math.sin(a), CY - 224 * math.cos(a))
    x0, y0 = math.floor(min(p1[0], p2[0])) - 2, math.floor(min(p1[1], p2[1])) - 2
    w = math.ceil(max(p1[0], p2[0])) + 2 - x0
    h = math.ceil(max(p1[1], p2[1])) + 2 - y0
    alpha = f"15 + 140 * pow(clamp(1 - (({NOW_DEG} - {deg} + 360) % 360) / {TRAIL_DEG}, 0, 1), 2)"
    return (f'    <PartDraw x="{x0}" y="{y0}" width="{w}" height="{h}" alpha="15">'
            f'<Transform target="alpha" value="{alpha}"/>'
            f'<Line startX="{p1[0] - x0:.1f}" startY="{p1[1] - y0:.1f}" endX="{p2[0] - x0:.1f}" endY="{p2[1] - y0:.1f}">'
            f'<Stroke color="#ffffffff" thickness="1.2" cap="BUTT"/></Line></PartDraw>')

# --- Dial (R7, R8): 15-minute ticks just inside the later lane, and hour numbers 1–12 in Anta.
FONT = "anta_regular"   # res/font/anta_regular.ttf (SIL OFL, see watchface/ANTA_FONT_LICENSE.txt)

def ticks():
    lines = []
    for q in range(48):
        kind = "hour" if q % 4 == 0 else "half" if q % 2 == 0 else "quarter"
        length = {"hour": 10, "half": 6, "quarter": 3}[kind]
        color = {"hour": "#ff8e8e93", "half": "#ff4a4a50", "quarter": "#ff33333a"}[kind]
        width = 2.5 if kind == "hour" else 1.5
        a = math.radians(q * 7.5)
        r0, r1 = R - 14, R - 14 - length
        lines.append(f'      <Line startX="{CX + r0 * math.sin(a):.1f}" startY="{CY - r0 * math.cos(a):.1f}" '
                     f'endX="{CX + r1 * math.sin(a):.1f}" endY="{CY - r1 * math.cos(a):.1f}">'
                     f'<Stroke color="{color}" thickness="{width}" cap="ROUND"/></Line>')
    return "\n".join(lines)

def numeral(h):
    a = math.radians(h * 30)
    x, y = CX + (R - 40) * math.sin(a), CY - (R - 40) * math.cos(a)
    color = "#ff8e8e93" if h % 3 == 0 else "#ff5c5c62"
    return (f'    <PartText x="{round(x - 22)}" y="{round(y - 16)}" width="44" height="32">'
            f'<Text align="CENTER"><Font family="{FONT}" size="24" color="{color}">'
            f'<Template><![CDATA[{h}]]></Template></Font></Text></PartText>')

# Center content (RULES §3, T2, M1), in the prototype's layout. S = selected (0/1).
# Status MAX = 1 + title width + 10000 × flags (1 arrow, 2 lock, 4 clash, 8 title scrolls); details MAX = 1 + width + 10000 × kind.
S = "[COMPLICATION.RANGED_VALUE_VALUE]"
F = "round(floor(([COMPLICATION.RANGED_VALUE_MAX] - 1) / 10000))"
W = "(([COMPLICATION.RANGED_VALUE_MAX] - 1) % 10000)"
ARROW = f"({F} % 2)"
LOCK = f"(floor({F} / 2) % 2)"
CLASH = f"(floor({F} / 4) % 2)"
SCROLL = f"(floor({F} / 8) % 2)"
SMALL = f"(floor({F} / 16) % 2)"   # C9: the title only fits at 24 px
EMPTY = f"(floor({F} / 32) % 2)"   # C3: no meetings, the clock takes the center
EXTRA = f"(30 * {ARROW} + 25 * {LOCK})"
ICON_X = f"(225 - ({W} + {EXTRA}) / 2)"

def text(x, y, w, h, size, color, expr, weight="NORMAL", family="SYNC_TO_DEVICE", extra=""):
    return (f'<PartText x="{x}" y="{y}" width="{w}" height="{h}">{extra}<Text align="CENTER" ellipsis="TRUE">'
            f'<Font family="{family}" size="{size}" weight="{weight}" color="{color}">'
            f'<Template><![CDATA[%s]]><Parameter expression="{expr}"/></Template></Font></Text></PartText>')

def details_group(name, inner):
    return f'''        <Group name="{name}" x="0" y="250" width="450" height="106" pivotX="0.5" pivotY="0.5">
          <Transform target="y" value="250 - 30 * {S}">{SELECT_ANIM}</Transform>
          <Transform target="scaleX" value="1 + 0.15 * {S}">{SELECT_ANIM}</Transform>
          <Transform target="scaleY" value="1 + 0.15 * {S}">{SELECT_ANIM}</Transform>
{inner}
        </Group>'''

def line_variants():
    """The place line in three colors, one shown per kind (C6, C7, C13); clash moves it under the title."""
    groups = []
    for n, cond, color in (("muted", f"({F} == 0 || {F} == 3)", "#ff8e8e93"),
                           ("clash", f"{F} == 1", "#ffff7a59"),
                           ("tomorrow", f"{F} == 2", LATER_TEXT)):
        groups.append(f'''          <Group name="line_{n}" x="0" y="74" width="450" height="30" alpha="0">
            <Transform target="x" value="{F} == 1 ? 15 : 0"/>
            <Transform target="y" value="{F} == 1 ? 44 : 74"/>
            <Transform target="alpha" value="{cond} ? 255 : 0"/>
            {text(0, 0, 450, 30, 24, color, "[COMPLICATION.TEXT]")}
          </Group>''')
    return chr(10).join(groups)

# Long titles (C9a). WFF has no marquee, so the full title is drawn by two extra slots (4: window without the
# look-ahead arrow, 6: the narrower window beside it) whose BoundingBox crops the text to the window (bounding
# areas crop drawing, see commit 37ad830). The text's x is a Transform on the clock: hold, glide, hold, fade out,
# jump back, fade in. MAX = 1 + overflow px + 10000 × scrolling (1); VALUE = selected moves it up like the details.
SPEED, HOLD1, HOLD2, FADE = 30, 1400, 1300, 260   # px/s, ms
SC_OVER = "(([COMPLICATION.RANGED_VALUE_MAX] - 1) % 10000)"
SC_ON = "floor(([COMPLICATION.RANGED_VALUE_MAX] - 1) / 10000)"

def scroll_slot(slot_id, name, service, arrow):
    left = 125 if arrow else 95
    glide = f"(clamp({SC_OVER}, 1, 9999) * {1000 / SPEED:.4f})"
    end = f"({HOLD1} + {glide} + {HOLD2})"
    # ms into the cycle (wraps hourly). [SECOND_MILLISECOND] is seconds with a fraction (e.g. 44.543), not ms.
    t = f"((([MINUTE] * 60 + [SECOND_MILLISECOND]) * 1000) % ({end} + {2 * FADE}))"
    u = f"clamp(({t} - {HOLD1}) / {glide}, 0, 1)"
    x = f"{left} - ({t} &lt; {end} + {FADE} ? {SC_OVER} * ({u} * {u} * (3 - 2 * {u})) : 0)"
    alpha = (f"{SC_ON} * 255 * ({t} &lt; {end} ? 1 : ({t} &lt; {end} + {FADE} ? 1 - ({t} - {end}) / {FADE} "
             f": ({t} - {end} - {FADE}) / {FADE}))")
    return f'''
    <!-- Slot `{name}` (C9a): the full title scrolling inside x {left}..355. Hidden in ambient (A2a shows the status title). -->
    <ComplicationSlot slotId="{slot_id}" name="{name}" displayName="@string/slot_title" x="0" y="0" width="450" height="450"
        supportedTypes="RANGED_VALUE EMPTY" isCustomizable="FALSE">
      {provider(service, "RANGED_VALUE")}
      <BoundingBox x="{left}" y="215" width="{355 - left}" height="85"/>
      <Complication type="RANGED_VALUE">
        <Group name="scroll_live" x="0" y="0" width="450" height="450">
          <Variant mode="AMBIENT" target="alpha" value="0"/>
          <PartText x="{left}" y="255" width="900" height="36" alpha="0">
            <Transform target="x" value="{x}"/>
            <Transform target="y" value="255 - 30 * {S}">{SELECT_ANIM}</Transform>
            <Transform target="alpha" value="{alpha}"/>
            <Text align="START"><Font family="SYNC_TO_DEVICE" size="24" weight="MEDIUM" color="#fff2f2f2">
              <Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TEXT]"/></Template></Font></Text>
          </PartText>
        </Group>
      </Complication>
    </ComplicationSlot>'''

def hotspot(h):
    a = math.radians(h * 30 + 15)                      # middle of the hour
    x = round(CX + (R - 12) * math.sin(a) - HOT / 2)
    y = round(CY - (R - 12) * math.cos(a) - HOT / 2)
    return f'''    <PartDraw name="hour_{h}" x="{x}" y="{y}" width="{HOT}" height="{HOT}">
      <Rectangle x="0" y="0" width="{HOT}" height="{HOT}"><Fill color="#00000000"/></Rectangle>
      <Launch target="app://{PKG}/select?hour={h}"/>
    </PartDraw>'''

selected = "[COMPLICATION.RANGED_VALUE_VALUE]"

# Colour themes (RULES §9), from the prototype: the watch's customize screen lists them as "Colours".
# Each option carries [meeting arcs, later lane, later/tomorrow text]; the ring image's themed panels are
# tinted with the first two (RingRenderer), so a theme needs no change in the data source.
THEMES = [
    ("amber", "theme_amber", ("#ffffb547", "#ff8fa3d6", "#ffa9b8e0")),
    ("mint", "theme_mint", ("#ff5fd4a0", "#ff8fb4d6", "#ffa9c8e0")),
    ("violet", "theme_violet", ("#ffb48cff", "#ff7fd3e0", "#ffa6e0ea")),
    ("rose", "theme_rose", ("#ffff7a9c", "#ffffc27a", "#ffffd5a6")),
]
EVENT, LANE_COLOR, LATER_TEXT = (f"[CONFIGURATION.themeColor.{i}]" for i in range(3))
THEME_XML = "\n".join(f'      <ColorOption id="{i}" displayName="{n}" colors="{" ".join(c)}"/>' for i, n, c in THEMES)
xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Phase 1 spike face (generated by tools/gen_spike_face.py; edit the script, not this file). -->
<WatchFace width="450" height="450">
  <UserConfigurations>
    <!-- Style (RULES §10): Calen-Time (theme-coloured arcs) or Time-Shift (arcs in the colour of the light) -->
    <ListConfiguration id="style" displayName="style_label" defaultValue="calen">
      <ListOption id="calen" displayName="style_calen"/>
      <ListOption id="shift" displayName="style_shift"/>
    </ListConfiguration>
    <ColorConfiguration id="themeColor" displayName="theme_label" defaultValue="amber">
{THEME_XML}
    </ColorConfiguration>
  </UserConfigurations>
  <Scene backgroundColor="#ff000000">

    <!-- Ring track (R1) -->
    <PartDraw x="0" y="0" width="450" height="450">
      <Arc centerX="{CX}" centerY="{CY}" width="{2*R}" height="{2*R}" startAngle="0" endAngle="360">
        <Stroke color="#ff1c1c1f" thickness="12"/>
      </Arc>
    </PartDraw>

    <!-- Risk 6: comet trail (R6). Face-side, driven by the clock only; under the ring image so it shows on free time. -->
{chr(10).join(dash(i) for i in range(240))}

    <!-- Dial: 15-minute ticks (R7) and hour numbers (R8) -->
    <PartDraw x="0" y="0" width="450" height="450">
{ticks()}
    </PartDraw>
{chr(10).join(numeral(h) for h in range(1, 13))}

    <!-- Slot 1 `ring` (spike 2): the whole ring as one image from the data source (fixes the 7-segment
         limit; carries lane, clash and private layers). SMALL_IMAGE: PHOTO_IMAGE is rejected for our provider. -->
    <ComplicationSlot slotId="0" name="ring" displayName="@string/slot_ring" x="0" y="0" width="450" height="450"
        supportedTypes="SMALL_IMAGE EMPTY" isCustomizable="FALSE">
      {provider("RingImageDataSourceService", "SMALL_IMAGE")}
      <BoundingOval x="0" y="0" width="450" height="450"/>
      <Complication type="SMALL_IMAGE">
        <!-- Four panels side by side (RingRenderer): each copy puts one on screen; the face multiplies by the tint.
             Meeting arcs: panel 0 tinted with the theme (Calen-Time) or panel 3 in the light's colours (Time-Shift). -->
        <ListConfiguration id="style">
          <ListOption id="calen">
            <PartImage x="0" y="0" width="1800" height="450" tintColor="{EVENT}">
              <Image resource="[COMPLICATION.SMALL_IMAGE]"/>
            </PartImage>
          </ListOption>
          <ListOption id="shift">
            <PartImage x="-1350" y="0" width="1800" height="450">
              <Image resource="[COMPLICATION.SMALL_IMAGE]"/>
            </PartImage>
          </ListOption>
        </ListConfiguration>
        <PartImage x="-450" y="0" width="1800" height="450" tintColor="{LANE_COLOR}">
          <Image resource="[COMPLICATION.SMALL_IMAGE]"/>
        </PartImage>
        <PartImage x="-900" y="0" width="1800" height="450">
          <Image resource="[COMPLICATION.SMALL_IMAGE]"/>
        </PartImage>
      </Complication>
    </ComplicationSlot>


    <!-- Round caps (R1a): the arc's ends are pulled in by the cap's angle, growing 0.55° → 1.57° with the stroke -->
    <!-- Slot 5 `ring_promote` (risk 4, M3): VALUE runs MIN (on the lane) → MAX (on the ring). The arc grows
         outward from R − 10 to R, thickens 4 → 12 px and crossfades blue → amber. -->
    <ComplicationSlot slotId="5" name="promote" displayName="@string/slot_promote" x="0" y="0" width="450" height="450"
        supportedTypes="RANGED_VALUE EMPTY" isCustomizable="FALSE">
      {provider("PromoteDataSourceService", "RANGED_VALUE")}
      <BoundingOval x="0" y="0" width="450" height="450"/>
      <Complication type="RANGED_VALUE">
        <Group name="settle" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5">
          <Transform target="scaleX" value="1 + {R / R_LATER - 1:.5f} * {P}">{SETTLE_ANIM}</Transform>
          <Transform target="scaleY" value="1 + {R / R_LATER - 1:.5f} * {P}">{SETTLE_ANIM}</Transform>
          <PartDraw x="0" y="0" width="450" height="450">
            <Transform target="alpha" value="255 * (1 - {P})">{SETTLE_ANIM}</Transform>
            <Arc centerX="{CX}" centerY="{CY}" width="{2*R_LATER}" height="{2*R_LATER}" startAngle="0" endAngle="1">
              <Transform target="startAngle" value="[COMPLICATION.RANGED_VALUE_MIN] + 1.55 + 1.02 * {P}"/>
              <Transform target="endAngle" value="[COMPLICATION.RANGED_VALUE_MAX] - 1.55 - 1.02 * {P}"/>
              <Stroke color="{LANE_COLOR}" thickness="4" cap="ROUND">
                <Transform target="thickness" value="4 + 8 * {P}">{SETTLE_ANIM}</Transform>
              </Stroke>
            </Arc>
          </PartDraw>
          <PartDraw x="0" y="0" width="450" height="450" alpha="0">
            <Transform target="alpha" value="255 * {P}">{SETTLE_ANIM}</Transform>
            <Arc centerX="{CX}" centerY="{CY}" width="{2*R_LATER}" height="{2*R_LATER}" startAngle="0" endAngle="1">
              <Transform target="startAngle" value="[COMPLICATION.RANGED_VALUE_MIN] + 1.55 + 1.02 * {P}"/>
              <Transform target="endAngle" value="[COMPLICATION.RANGED_VALUE_MAX] - 1.55 - 1.02 * {P}"/>
              <Stroke color="{EVENT}" thickness="4" cap="ROUND">
                <Transform target="thickness" value="4 + 8 * {P}">{SETTLE_ANIM}</Transform>
              </Stroke>
            </Arc>
          </PartDraw>
        </Group>
      </Complication>
    </ComplicationSlot>

    <!-- Now dot (R6): rotates with the clock; above the ring layers -->
    <Group name="now" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="0.5">
      <Transform target="angle" value="{NOW_DEG}"/>
      <PartDraw x="{CX - 14}" y="{CY - R - 14}" width="28" height="28">
        <Ellipse x="3" y="3" width="22" height="22"><Fill color="#2effffff"/></Ellipse>
        <Ellipse x="7" y="7" width="14" height="14"><Fill color="#ff000000"/></Ellipse>
        <Ellipse x="9.5" y="9.5" width="9" height="9"><Fill color="#ffffffff"/></Ellipse>
      </PartDraw>
    </Group>

    <!-- Slot 4 `status` (risks 2 + 3): VALUE = selected (0/1). Date, clock, divider, title and time range live
         inside this slot so their Transforms can read it (M1). MAX carries the title width and icon flags. -->
    <ComplicationSlot slotId="1" name="status" displayName="@string/slot_status" x="0" y="0" width="450" height="450"
        supportedTypes="RANGED_VALUE EMPTY" isCustomizable="FALSE">
      {provider("StatusDataSourceService", "RANGED_VALUE")}
      <BoundingOval x="0" y="0" width="450" height="450"/>
      <Complication type="RANGED_VALUE">
        <!-- Day + date (C1, T1): 24 px, muted; shrinks to 90 % in place when selected; down 55 px when empty (C3) -->
        <Group name="date" x="0" y="98" width="450" height="30" pivotX="0.5" pivotY="0.5">
          <Transform target="y" value="98 + 55 * {EMPTY}">{SELECT_ANIM}</Transform>
          <Transform target="scaleX" value="1 - 0.1 * {S}">{SELECT_ANIM}</Transform>
          <Transform target="scaleY" value="1 - 0.1 * {S}">{SELECT_ANIM}</Transform>
          <PartText x="0" y="0" width="450" height="30">
            <Text align="CENTER">
              <Font family="SYNC_TO_DEVICE" size="24" weight="MEDIUM" color="#ff8e8e93" letterSpacing="0.08">
                <Upper><Template><![CDATA[%s · %s]]><Parameter expression="icuText(&quot;EEE&quot;)"/><Parameter expression="icuText(&quot;MMM d&quot;)"/></Template></Upper>
              </Font>
            </Text>
          </PartText>
        </Group>
        <!-- Clock (T5): Anta 88 px; selected: 55 %, up 10 px; empty (C3): 115 %, down 60 px to the center -->
        <Group name="clock" x="0" y="119" width="450" height="110" pivotX="0.5" pivotY="0.5">
          <Transform target="y" value="119 - 10 * {S} + 60 * {EMPTY}">{SELECT_ANIM}</Transform>
          <Transform target="scaleX" value="1 - 0.45 * {S} + 0.15 * {EMPTY}">{SELECT_ANIM}</Transform>
          <Transform target="scaleY" value="1 - 0.45 * {S} + 0.15 * {EMPTY}">{SELECT_ANIM}</Transform>
          <!-- AM/PM (C11): WFF can't measure text, so time + AM/PM are centered as a unit with fixed widths. The time is
               right-aligned to a seam; AM/PM starts 8 px after it. A two-digit hour (10-12) is ~32 px wider, so both
               move 16 px right (half the extra width). Widths measured from the prototype's Anta render: 1-digit
               hour ~185 px, 2-digit ~217, AM/PM ~38. Re-check on the emulator. -->
          <Group name="clock_time" x="{SEAM - 450}" y="0" width="450" height="110">
            <Transform target="x" value="{SEAM - 450} + 16 * floor([HOUR_1_12] / 10)"/>
            <DigitalClock x="0" y="0" width="450" height="110">
              <TimeText x="0" y="0" width="450" height="110" format="h:mm" hourFormat="TWELVE" align="END">
                <Font family="{FONT}" size="88" color="#fff2f2f2"/>
              </TimeText>
            </DigitalClock>
          </Group>
          <Group name="clock_ampm" x="{SEAM + 8}" y="62" width="60" height="30">
            <Transform target="x" value="{SEAM + 8} + 16 * floor([HOUR_1_12] / 10)"/>
            <PartText x="0" y="0" width="60" height="30">
              <Text align="START">
                <Font family="SYNC_TO_DEVICE" size="24" weight="MEDIUM" color="#ff8e8e93">
                  <Upper><Template><![CDATA[%s]]><Parameter expression="icuText(&quot;a&quot;)"/></Template></Upper>
                </Font>
              </Text>
            </PartText>
          </Group>
        </Group>
        <!-- Divider (T2): selected: up 32 px, 60 % wide; fades out when empty (C3) -->
        <Group name="divider" x="{CX - 70}" y="236" width="140" height="4" pivotX="0.5" pivotY="0.5">
          <Transform target="alpha" value="255 * (1 - {EMPTY})">{SELECT_ANIM}</Transform>
          <Transform target="y" value="236 - 32 * {S}">{SELECT_ANIM}</Transform>
          <Transform target="scaleX" value="1 - 0.4 * {S}">{SELECT_ANIM}</Transform>
          <PartDraw x="0" y="0" width="140" height="4">
            <Line startX="1" startY="2" endX="139" endY="2"><Stroke color="#ff2a2a2e" thickness="2" cap="ROUND"/></Line>
          </PartDraw>
        </Group>
        <!-- Title with the look-ahead arrow (C12) and private lock (C13) before it, then the time range (C4, C8).
             On a double booking the range moves down a row so the clash line sits under the title (C7). -->
{details_group("details", f"""          <!-- Live title: hidden while the full title scrolls in slot 4/6 (C9a), and in ambient -->
          <!-- C9: 30 px, or 24 px when it only fits at that size (flag 16); one of the two is visible -->
          <Group name="title_live" x="0" y="0" width="450" height="60">
            <Variant mode="AMBIENT" target="alpha" value="0"/>
            <PartText x="0" y="5" width="450" height="36">
              <Transform target="x" value="{EXTRA} / 2"/>
              <Transform target="alpha" value="255 * (1 - {SCROLL}) * (1 - {SMALL})"/>
              <Text align="CENTER" ellipsis="TRUE"><Font family="SYNC_TO_DEVICE" size="30" weight="MEDIUM" color="#fff2f2f2">
                <Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TEXT]"/></Template></Font></Text>
            </PartText>
            <PartText x="0" y="5" width="450" height="36">
              <Transform target="x" value="{EXTRA} / 2"/>
              <Transform target="alpha" value="255 * (1 - {SCROLL}) * {SMALL}"/>
              <Text align="CENTER" ellipsis="TRUE"><Font family="SYNC_TO_DEVICE" size="24" weight="MEDIUM" color="#fff2f2f2">
                <Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TEXT]"/></Template></Font></Text>
            </PartText>
          </Group>
          <!-- Ambient title (A2a): the shortened title, first characters and an ellipsis; nothing moves -->
          <Group name="title_ambient" x="0" y="0" width="450" height="60" alpha="0">
            <Variant mode="AMBIENT" target="alpha" value="255"/>
            <PartText x="0" y="5" width="450" height="36">
              <Transform target="x" value="{EXTRA} / 2"/>
              <Transform target="alpha" value="255 * (1 - {SMALL})"/>
              <Text align="CENTER" ellipsis="TRUE"><Font family="SYNC_TO_DEVICE" size="30" weight="MEDIUM" color="#fff2f2f2">
                <Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TEXT]"/></Template></Font></Text>
            </PartText>
            <PartText x="0" y="5" width="450" height="36">
              <Transform target="x" value="{EXTRA} / 2"/>
              <Transform target="alpha" value="255 * {SMALL}"/>
              <Text align="CENTER" ellipsis="TRUE"><Font family="SYNC_TO_DEVICE" size="24" weight="MEDIUM" color="#fff2f2f2">
                <Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TEXT]"/></Template></Font></Text>
            </PartText>
          </Group>
          <PartDraw name="arrow" x="0" y="13" width="20" height="20" alpha="0">
            <Transform target="x" value="{ICON_X}"/>
            <Transform target="alpha" value="255 * {ARROW}"/>
            <Line startX="1.5" startY="10" endX="18.5" endY="10"><Stroke color="{EVENT}" thickness="3" cap="ROUND"/></Line>
            <Line startX="12" startY="3.5" endX="18.5" endY="10"><Stroke color="{EVENT}" thickness="3" cap="ROUND"/></Line>
            <Line startX="12" startY="16.5" endX="18.5" endY="10"><Stroke color="{EVENT}" thickness="3" cap="ROUND"/></Line>
          </PartDraw>
          <PartDraw name="lock" x="0" y="12" width="16" height="22" alpha="0">
            <Transform target="x" value="{ICON_X} + 30 * {ARROW}"/>
            <Transform target="alpha" value="255 * {LOCK}"/>
            <Arc centerX="8" centerY="9" width="9" height="9" startAngle="-90" endAngle="90"><Stroke color="#ff8e8e93" thickness="2.5" cap="BUTT"/></Arc>
            <RoundRectangle x="0" y="9" width="16" height="12" cornerRadiusX="2.5" cornerRadiusY="2.5"><Fill color="#ff8e8e93"/></RoundRectangle>
          </PartDraw>
          <PartText x="0" y="44" width="450" height="30">
            <Transform target="y" value="44 + 30 * {CLASH}"/>
            <Text align="CENTER" ellipsis="TRUE"><Font family="SYNC_TO_DEVICE" size="24" color="#fff2f2f2">
              <Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TITLE]"/></Template></Font></Text>
          </PartText>""")}
      </Complication>
    </ComplicationSlot>

    <!-- Slot 3 `details`: the line under the time range. MAX = 1 + width + 10000 × kind
         (0 place, muted · 1 clash, coral, with the overlap icon, and moved under the title (C7) ·
         2 "Tomorrow · place", blue (C6) · 3 "Details hidden", muted (C13)). Same layout motion as the status slot. -->
    <ComplicationSlot slotId="3" name="details" displayName="@string/slot_details" x="0" y="0" width="450" height="450"
        supportedTypes="RANGED_VALUE EMPTY" isCustomizable="FALSE">
      {provider("DetailsDataSourceService", "RANGED_VALUE")}
      <BoundingOval x="0" y="0" width="450" height="450"/>
      <Complication type="RANGED_VALUE">
{details_group("line", f"""          <PartDraw name="overlap" x="0" y="52" width="22" height="14" alpha="0">
            <Transform target="x" value="225 - ({W} + 30) / 2"/>
            <Transform target="alpha" value="{F} == 1 ? 255 : 0"/>
            <Ellipse x="1" y="1" width="12" height="12"><Stroke color="#ffff7a59" thickness="2"/></Ellipse>
            <Ellipse x="9" y="1" width="12" height="12"><Stroke color="#ffff7a59" thickness="2"/></Ellipse>
          </PartDraw>
""" + line_variants())}
      </Complication>
    </ComplicationSlot>

{scroll_slot(4, 'title_scroll', 'TitleScrollDataSourceService', False)}
{scroll_slot(6, 'title_scroll_arrow', 'TitleScrollArrowDataSourceService', True)}

    <!-- Risks 1 + 7: twelve transparent one-hour hotspots on the ring, each a Launch deep link (I1) -->
{chr(10).join(hotspot(h) for h in range(12))}
    <!-- I8: the details area (inside the hour hotspots). A tap while a meeting is selected goes back to the normal view. -->
    <PartDraw name="details_tap" x="105" y="205" width="240" height="130">
      <Rectangle x="0" y="0" width="240" height="130"><Fill color="#00000000"/></Rectangle>
      <Launch target="app://{PKG}/release"/>
    </PartDraw>
  </Scene>
</WatchFace>
'''
(res / "raw").mkdir(parents=True, exist_ok=True)
(res / "raw" / "watchface.xml").write_text(xml)

# Preview: black face with the track and two amber meeting arcs (1–2, 4–5).
W = 450
px = bytearray(b"\x00\x00\x00" * W * W)
for y in range(W):
    for x in range(W):
        d = math.hypot(x - CX, y - CY)
        if 213 <= d <= 225:
            ang = (math.degrees(math.atan2(x - CX, CY - y)) + 360) % 360
            c = (0xff, 0xb5, 0x47) if (30 <= ang <= 60 or 120 <= ang <= 150) else (0x1c, 0x1c, 0x1f)
            i = (y * W + x) * 3
            px[i:i + 3] = bytes(c)
raw = b"".join(b"\x00" + bytes(px[y * W * 3:(y + 1) * W * 3]) for y in range(W))
def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, W, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
(res / "drawable").mkdir(parents=True, exist_ok=True)
(res / "drawable" / "preview.png").write_bytes(png)
print("wrote", res / "raw" / "watchface.xml", "and preview.png")
