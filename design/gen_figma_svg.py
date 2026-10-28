"""Generates Figma-friendly SVGs of the Calen-Time watch face.

Usage: python3 design/gen_figma_svg.py   (writes design/watchface-default.svg, watchface-selected.svg and
watchface-timeshift.svg)

Geometry, colors and type follow RULES.md §2–§4 on the 450 × 450 canvas, using the watch build's sample
day (android/…/SpikeSchedule.kt) at 10:20 AM. Built for editing in Figma: named groups (`id` becomes the
layer name), real text (Anta for the clock and numbers, Roboto standing in for the watch's system font),
plain shapes with inline attributes, no CSS. Private stripes are lines inside a clip path, which Figma
imports as a mask group.

The Time-Shift file (RULES §10) is the watch's rolling busy day at 10:13 AM in the Time-Shift colourway. SVG has
no sweep gradient, so each meeting arc gets a linear gradient from its start to its end, sampled every few
minutes from the day's colours (S3) and projected onto that chord; Figma imports it as an editable gradient.
"""
import math
from pathlib import Path

OUT = Path(__file__).parent
C = 225
R = 219            # band 213–225, flush with the edge (R1)
R_LATER = R - 10   # later lane (R5)
DIAL = 720         # minutes per turn
GAP = 1            # degrees at each arc end (R2)
NOW = 10 * 60 + 20

AMBER, AMBER_PAST = "#ffb547", "#6e4f22"
LATER, CLASH = "#8fa3d6", "#ff7a59"
TEXT, MUTED, TRACK = "#f2f2f2", "#8e8e93", "#1c1c1f"
SANS = "Roboto"     # stand-in for the watch's system font (SYNC_TO_DEVICE)
DISPLAY = "Anta"    # clock digits and dial numbers (T5)


def deg(minutes):
    return (minutes % DIAL) / DIAL * 360


def pt(angle, r):
    a = math.radians(angle)
    return C + r * math.sin(a), C - r * math.cos(a)


def arc_d(from_min, to_min, r, gap=GAP):
    a0 = deg(from_min) + gap
    sweep = max(0.5, (to_min - from_min) / DIAL * 360 - 2 * gap)
    (x0, y0), (x1, y1) = pt(a0, r), pt(a0 + sweep, r)
    return f"M{x0:.2f} {y0:.2f} A{r} {r} 0 {1 if sweep > 180 else 0} 1 {x1:.2f} {y1:.2f}"


def arc(name, from_min, to_min, r, color, width, cap="butt"):
    return (f'<path id="{name}" d="{arc_d(from_min, to_min, r)}" fill="none" stroke="{color}" '
            f'stroke-width="{width}" stroke-linecap="{cap}"/>')


def band_d(from_min, to_min, r, width):
    """Closed annular sector, used as the clip shape for private stripes."""
    a0, a1 = deg(from_min) + GAP, deg(from_min) + (to_min - from_min) / DIAL * 360 - GAP
    ro, ri = r + width / 2, r - width / 2
    (x0, y0), (x1, y1) = pt(a0, ro), pt(a1, ro)
    (x2, y2), (x3, y3) = pt(a1, ri), pt(a0, ri)
    return (f"M{x0:.2f} {y0:.2f} A{ro} {ro} 0 0 1 {x1:.2f} {y1:.2f} L{x2:.2f} {y2:.2f} "
            f"A{ri} {ri} 0 0 0 {x3:.2f} {y3:.2f} Z")


def private_arc(name, from_min, to_min, color, clip_id):
    """For private-meeting variants. Caution band (R10): 5 px stripes / 5 px gaps at 45° to the arc's middle tangent."""
    mid = deg((from_min + to_min) / 2)
    cx, cy = pt(mid, R)
    lines = []
    for k in range(-8, 9):
        # Stripes run along (mid + 45)°; spaced 10 px apart across that direction.
        a = math.radians(mid + 45)
        dx, dy = math.cos(a), math.sin(a)
        ox, oy = -dy * k * 10, dx * k * 10
        lines.append(f'<line x1="{cx + ox - dx * 40:.2f}" y1="{cy + oy - dy * 40:.2f}" '
                     f'x2="{cx + ox + dx * 40:.2f}" y2="{cy + oy + dy * 40:.2f}" stroke="{color}" stroke-width="5"/>')
    return (f'<clipPath id="{clip_id}"><path d="{band_d(from_min, to_min, R, 12)}"/></clipPath>'
            f'<g id="{name}" clip-path="url(#{clip_id})">{"".join(lines)}</g>')


def clash(name, from_min, to_min):
    """Double booking (R4): blank the band, two 5 px coral stripes at R ± 4.5."""
    return (f'<g id="{name}">' + arc("Gap", from_min, to_min, R, "#000000", 14)
            + arc("Stripe outer", from_min, to_min, R + 4.5, CLASH, 5)
            + arc("Stripe inner", from_min, to_min, R - 4.5, CLASH, 5) + "</g>")


def text(name, x, y, size, color, content, family=SANS, weight=400, anchor="middle", spacing=0):
    return (f'<text id="{name}" x="{x:.1f}" y="{y:.1f}" font-family="{family}" font-size="{size:g}" '
            f'font-weight="{weight}" fill="{color}" text-anchor="{anchor}" letter-spacing="{spacing:g}">{content}</text>')


def dial_layers(now=NOW):
    trail = []
    now_deg = deg(now)
    for i in range(240):
        d_deg = i * 1.5
        behind = (now_deg - d_deg + 360) % 360
        alpha = (15 + 140 * max(0.0, 1 - behind / 55) ** 2) / 255
        (x0, y0), (x1, y1) = pt(d_deg, 214), pt(d_deg, 224)
        trail.append(f'<line x1="{x0:.2f}" y1="{y0:.2f}" x2="{x1:.2f}" y2="{y1:.2f}" stroke="#ffffff" '
                     f'stroke-width="1.2" stroke-opacity="{alpha:.3f}"/>')
    ticks = []
    for q in range(48):
        kind = "hour" if q % 4 == 0 else "half" if q % 2 == 0 else "quarter"
        length = {"hour": 10, "half": 6, "quarter": 3}[kind]
        color = {"hour": "#8e8e93", "half": "#4a4a50", "quarter": "#33333a"}[kind]
        (x0, y0), (x1, y1) = pt(q * 7.5, R - 14), pt(q * 7.5, R - 14 - length)
        ticks.append(f'<line x1="{x0:.2f}" y1="{y0:.2f}" x2="{x1:.2f}" y2="{y1:.2f}" stroke="{color}" '
                     f'stroke-width="{2.5 if kind == "hour" else 1.5}" stroke-linecap="round"/>')
    numbers = []
    for h in range(1, 13):
        x, y = pt(h * 30, R - 40)
        numbers.append(text(f"{h}", x, y + 8.5, 24, MUTED if h % 3 == 0 else "#5c5c62", str(h), family=DISPLAY))
    return (f'<circle id="Track" cx="{C}" cy="{C}" r="{R}" fill="none" stroke="{TRACK}" stroke-width="12"/>'
            f'<g id="Comet trail">{"".join(trail)}</g>'
            f'<g id="Ticks">{"".join(ticks)}</g>'
            f'<g id="Hour numbers">{"".join(numbers)}</g>')


def meetings():
    """The sample day at 10:20 AM (RULES W1–W8): Standup is past; Roadmap is live; Late sync is on the lane."""
    amber = AMBER
    parts = [
        arc("Roadmap planning · elapsed", 600, NOW, R, AMBER_PAST, 12),
        arc("Roadmap planning · remaining", NOW, 690, R, amber, 12),
        arc("Coffee Catch Up", 750, 780, R, amber, 12),
        arc("Design review", 780, 840, R, amber, 12),
        arc("Vendor call", 780, 840, R, amber, 12),
        arc("Candidate interview", 900, 945, R, amber, 12),
        arc("Recruiter sync", 930, 960, R, amber, 12),
        arc("Weekly sync", 960, 1020, R, amber, 12),
        arc("Client call", 1140, 1200, R, amber, 12),
        clash("Double booking 1–2 PM", 780, 840),
        clash("Double booking 3:30–3:45 PM", 930, 945),
        arc("Late sync (later lane)", 1380, 1425, R_LATER, LATER, 4, cap="round"),
    ]
    return f'<g id="Meetings">{"".join(parts)}</g>'


def now_dot(now=NOW):
    x, y = pt(deg(now), R)
    return (f'<g id="Now dot"><circle cx="{x:.2f}" cy="{y:.2f}" r="11" fill="#ffffff" fill-opacity="0.18"/>'
            f'<circle cx="{x:.2f}" cy="{y:.2f}" r="7" fill="#000000"/>'
            f'<circle cx="{x:.2f}" cy="{y:.2f}" r="4.5" fill="#ffffff"/></g>')


def arrow(x, y, s=1.0, color=AMBER):
    """Look-ahead arrow (C12): 20 × 20, 3 px amber, drawn from its left edge x and vertical center y."""
    def ln(x1, y1, x2, y2):
        return (f'<line x1="{x + x1 * s:.2f}" y1="{y + y1 * s:.2f}" x2="{x + x2 * s:.2f}" y2="{y + y2 * s:.2f}" '
                f'stroke="{color}" stroke-width="{3 * s:g}" stroke-linecap="round"/>')
    return f'<g id="Arrow (next)">{ln(1.5, 0, 18.5, 0)}{ln(12, -6.5, 18.5, 0)}{ln(12, 6.5, 18.5, 0)}</g>'


def lock(x, top, s=1.0):
    """For private-meeting variants. Private lock (C13): 16 × 20, grey."""
    return (f'<g id="Lock (private)"><path d="M{x + 3.5 * s:.2f} {top + 9 * s:.2f} V{top + 6.5 * s:.2f} '
            f'a{4.5 * s} {4.5 * s} 0 0 1 {9 * s:.2f} 0 V{top + 9 * s:.2f}" fill="none" stroke="{MUTED}" stroke-width="{2.5 * s:g}"/>'
            f'<rect x="{x:.2f}" y="{top + 9 * s:.2f}" width="{16 * s:g}" height="{12 * s:g}" rx="{2.5 * s:g}" fill="{MUTED}"/></g>')


def overlap_icon(x, y, s=1.0):
    """Double-booking icon (C7): two intersecting rings, 22 px wide, coral."""
    return (f'<g id="Overlap icon"><circle cx="{x + 7 * s:.2f}" cy="{y:.2f}" r="{6 * s:g}" fill="none" stroke="{CLASH}" stroke-width="{2 * s:g}"/>'
            f'<circle cx="{x + 15 * s:.2f}" cy="{y:.2f}" r="{6 * s:g}" fill="none" stroke="{CLASH}" stroke-width="{2 * s:g}"/></g>')


def default_center():
    """C1–C12 default layout (T2): next meeting is Coffee Catch Up, with the arrow because Roadmap is live."""
    title_w, extra = 219, 30              # "Coffee Catch Up" at 30 px Roboto Medium (measured); arrow space
    icons_x = C - (title_w + extra) / 2
    return ('<g id="Center">'
            + text("Date", C, 122, 24, MUTED, "SUN · OCT 25", weight=500, spacing=2)
            + '<g id="Clock">'
            + text("Time", 203, 206, 88, TEXT, "10:20", family=DISPLAY)
            + text("AM/PM", 322, 206, 24, MUTED, "AM", weight=500, anchor="start") + "</g>"
            + f'<line id="Divider" x1="155" y1="238" x2="295" y2="238" stroke="#2a2a2e" stroke-width="2" stroke-linecap="round"/>'
            + '<g id="Details">'
            + arrow(icons_x, 273.5)
            + text("Title", C + extra / 2, 284, 30, TEXT, "Coffee Catch Up", weight=500)
            + text("Time range", C, 318, 24, TEXT, "12:30 – 1:00 PM")
            + text("Place", C, 348, 24, MUTED, "Café Blue")
            + "</g></g>")


def selected_center():
    """Selected state (M1, C7): Design review, double-booked with Vendor call. Clock 55 % and up 10 px,
    date 90 % in place, divider up 32 px at 60 %, details 115 % and up 30 px."""
    s = 1.15
    def y(base):  # details scale about (225, 310), then up 30
        return 310 + (base - 310) * s - 30
    clash_w = 125 * s                     # "Vendor call" at 24 px, scaled
    icon_x = C - (clash_w + 30 * s) / 2
    return ('<g id="Center">'
            + text("Date", C, 122 - 1.2, 24 * 0.9, MUTED, "SUN · OCT 25", weight=500, spacing=1.8)
            + '<g id="Clock">'
            + text("Time", 213, 170 + 36 * 0.55 - 10, 88 * 0.55, TEXT, "10:20", family=DISPLAY)
            + text("AM/PM", 278, 170 + 36 * 0.55 - 10, 24 * 0.55, MUTED, "AM", weight=500, anchor="start") + "</g>"
            + f'<line id="Divider" x1="183" y1="206" x2="267" y2="206" stroke="#2a2a2e" stroke-width="2" stroke-linecap="round"/>'
            + '<g id="Details">'
            + text("Title", C, y(284), 30 * s, TEXT, "Design review", weight=500)
            + overlap_icon(icon_x, y(318) - 9 * s, s)
            + text("Clash", C + 15 * s, y(318), 24 * s, CLASH, "Vendor call")
            + text("Time range", C, y(348), 24 * s, TEXT, "1:00 – 2:00 PM")
            + "</g></g>")


# ---- Time-Shift (RULES §10) ----------------------------------------------------------------------

def sun_times(lat=20.59, lon=78.96, y=2026, mo=10, d=27, tz=5.5):
    """Sunrise, solar noon, sunset in local minutes (S1; same formula as the watch's SunColors)."""
    rad, j1970, j2000 = math.pi / 180, 2440588, 2451545
    import datetime
    noon_utc = datetime.datetime(y, mo, d, 12, tzinfo=datetime.timezone(datetime.timedelta(hours=tz))).timestamp()
    to_days = noon_utc / 86400 - 0.5 + j1970 - j2000
    lw, phi = rad * -lon, rad * lat
    n = round(to_days - 0.0009 - lw / (2 * math.pi))
    ds = 0.0009 + lw / (2 * math.pi) + n
    m = rad * (357.5291 + 0.98560028 * ds)
    l = m + rad * (1.9148 * math.sin(m) + 0.02 * math.sin(2 * m) + 0.0003 * math.sin(3 * m)) + rad * 102.9372 + math.pi
    dec = math.asin(math.sin(rad * 23.4397) * math.sin(l))
    transit = lambda j: j2000 + j + 0.0053 * math.sin(m) - 0.0069 * math.sin(2 * l)
    w = math.acos((math.sin(-0.833 * rad) - math.sin(phi) * math.sin(dec)) / (math.cos(phi) * math.cos(dec)))
    noon = transit(ds)
    sset = transit(0.0009 + (w + lw) / (2 * math.pi) + n)
    local = lambda j: ((j + 0.5 - j1970) * 86400 / 60 + tz * 60) % 1440
    return local(noon - (sset - noon)), local(noon), local(sset)


SR, SUN_NOON, SS = sun_times()
SUN_STOPS = sorted([(180, "#2f72cf"), (SR - 80, "#3a9ad0"), (SR - 40, "#8f7ad8"), (SR - 12, "#e88ab2"), (SR + 15, "#ffa86b"),
                    (SR + 70, "#ffcc5c"), (SR + 180, "#fff06a"), (SUN_NOON, "#fff6cc"), (SUN_NOON + 105, "#ffe08a"),
                    (SS - 150, "#ffbb4d"), (SS - 80, "#ff983c"), (SS - 30, "#ff7438"), (SS, "#ff5257"), (SS + 20, "#ec4f92"),
                    (SS + 45, "#a957dc"), (SS + 80, "#6c5ae8"), (21 * 60, "#3f66e6")])
SUN_STOPS = [(0, "#4a3ccf")] + SUN_STOPS + [(1440, "#4a3ccf")]


def _lab(h):
    lin = lambda c: c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(int(h[i:i + 2], 16) / 255) for i in (1, 3, 5))
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s_ = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s_, 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s_,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s_)


def _hex(lab, factor=1.0):
    L, a, b = lab
    l, m, s_ = ((L + 0.3963377774 * a + 0.2158037573 * b) ** 3, (L - 0.1055613458 * a - 0.0638541728 * b) ** 3,
                (L - 0.0894841775 * a - 1.2914855480 * b) ** 3)
    srgb = lambda c: 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
    rgb = (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s_, -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s_,
           -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s_)
    return "#" + "".join(f"{round(min(1, max(0, srgb(max(0, c)))) * 255 * factor):02x}" for c in rgb)


def sun_color(t, factor=1.0):
    """The light's colour at minute t of the day (S3), blended in OKLab; factor darkens (elapsed parts)."""
    t %= 1440
    i = 1
    while i < len(SUN_STOPS) - 1 and SUN_STOPS[i][0] < t:
        i += 1
    (m0, c0), (m1, c1) = SUN_STOPS[i - 1], SUN_STOPS[i]
    f = (t - m0) / (m1 - m0) if m1 > m0 else 0
    a, b = _lab(c0), _lab(c1)
    return _hex(tuple(a[k] + (b[k] - a[k]) * f for k in range(3)), factor)


def sun_arc(name, from_min, to_min, factor=1.0):
    """A meeting arc (12 px, round ends, R1a) painted with the day's colours from its start to its end."""
    cap = math.degrees(6 / R)                                     # round cap reaches 6 px past the path
    a0 = deg(from_min) + GAP + cap
    a1 = max(a0 + 0.01, deg(from_min) + (to_min - from_min) / DIAL * 360 - GAP - cap)
    (x0, y0), (x1, y1) = pt(a0, R), pt(a1, R)
    gid = "g" + "".join(ch for ch in name if ch.isalnum())
    vx, vy = x1 - x0, y1 - y0
    vv = vx * vx + vy * vy or 1
    stops = []
    steps = max(2, int((to_min - from_min) / 5))
    for k in range(steps + 1):
        t = from_min + (to_min - from_min) * k / steps
        px, py = pt(deg(t), R)
        off = min(1, max(0, ((px - x0) * vx + (py - y0) * vy) / vv))
        stops.append(f'<stop offset="{off:.3f}" stop-color="{sun_color(t, factor)}"/>')
    grad = (f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x0:.2f}" y1="{y0:.2f}" x2="{x1:.2f}" '
            f'y2="{y1:.2f}">{"".join(stops)}</linearGradient>')
    d = f"M{x0:.2f} {y0:.2f} A{R} {R} 0 {1 if a1 - a0 > 180 else 0} 1 {x1:.2f} {y1:.2f}"
    return grad + f'<path id="{name}" d="{d}" fill="none" stroke="url(#{gid})" stroke-width="12" stroke-linecap="round"/>'


TS_NOW = 10 * 60 + 13
TS_ACCENT = "#ff9447"     # Time-Shift colourway's arrow (golden-hour orange)


def timeshift_meetings():
    """The watch's rolling busy day made at 10 AM, seen at 10:13 (Standup live; window 9:30 AM–9:30 PM)."""
    parts = [
        sun_arc("Standup · elapsed", 570, TS_NOW, 0.45),
        sun_arc("Standup · remaining", TS_NOW, 630),
        sun_arc("Roadmap planning", 645, 705),
        private_arc("Lunch with Sam (private)", 705, 735, MUTED, "clipLunch"),
        sun_arc("Design review", 750, 810),
        sun_arc("Vendor call", 750, 810),
        sun_arc("Candidate interview", 840, 885),
        sun_arc("Recruiter sync", 870, 900),
        sun_arc("Weekly sync", 900, 960),
        sun_arc("Quarterly roadmap and budget alignment with leadership", 990, 1080),
        sun_arc("Client call", 1110, 1170),
        sun_arc("Product demo", 1200, 1260),
        clash("Double booking 12:30–1:30 PM", 750, 810),
        clash("Double booking 2:30–2:45 PM", 870, 885),
        arc("Late sync (later lane)", 1380, 1440, R_LATER, LATER, 4, cap="round"),
    ]
    return f'<g id="Meetings">{"".join(parts)}</g>'


def timeshift_center():
    """Default layout: Standup is live, so the next meeting gets the arrow; its title fits at 24 px (C9)."""
    title_w, extra = 200, 30              # "Roadmap planning" at 24 px Roboto Medium (measured on the watch); arrow space
    icons_x = C - (title_w + extra) / 2
    return ('<g id="Center">'
            + text("Date", C, 122, 24, MUTED, "TUE · OCT 27", weight=500, spacing=2)
            + '<g id="Clock">'
            + text("Time", 203, 206, 88, TEXT, "10:13", family=DISPLAY)
            + text("AM/PM", 322, 206, 24, MUTED, "AM", weight=500, anchor="start") + "</g>"
            + f'<line id="Divider" x1="155" y1="238" x2="295" y2="238" stroke="#2a2a2e" stroke-width="2" stroke-linecap="round"/>'
            + '<g id="Details">'
            + arrow(icons_x, 273.5, color=TS_ACCENT)
            + text("Title", C + extra / 2, 282, 24, TEXT, "Roadmap planning", weight=500)
            + text("Time range", C, 318, 24, TEXT, "10:45 – 11:45 AM")
            + text("Place", C, 348, 24, MUTED, "Atlas room")
            + "</g></g>")


def timeshift_face():
    sun = lambda m: f"{int(m // 60) % 12 or 12}:{int(m % 60):02d}"
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="450" height="450" viewBox="0 0 450 450">
<!-- Time-Shift colours for central India on Oct 27: sunrise {sun(SR)} AM, solar noon {sun(SUN_NOON)}, sunset {sun(SS)} PM -->
<g id="Calen-Time · Time-Shift 10:13 AM">
<circle id="Screen" cx="{C}" cy="{C}" r="225" fill="#000000"/>
{dial_layers(TS_NOW)}
{timeshift_meetings()}
{now_dot(TS_NOW)}
{timeshift_center()}
</g>
</svg>
'''


def face(name, center, dim):
    ring = meetings()
    if dim:  # R9: everything else at 35 %, the selected meeting on top exactly as it normally looks
        ring = f'<g id="Dimmed ring" opacity="0.35">{ring}</g>' + arc("Selected meeting", 780, 840, R, AMBER, 12)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="450" height="450" viewBox="0 0 450 450">
<g id="{name}">
<circle id="Screen" cx="{C}" cy="{C}" r="225" fill="#000000"/>
{dial_layers()}
{ring}
{now_dot()}
{center}
</g>
</svg>
'''


(OUT / "watchface-default.svg").write_text(face("Calen-Time · default 10:20 AM", default_center(), dim=False))
(OUT / "watchface-selected.svg").write_text(face("Calen-Time · selected Design review", selected_center(), dim=True))
(OUT / "watchface-timeshift.svg").write_text(timeshift_face())
print("wrote", OUT / "watchface-default.svg", OUT / "watchface-selected.svg", "and", OUT / "watchface-timeshift.svg")
