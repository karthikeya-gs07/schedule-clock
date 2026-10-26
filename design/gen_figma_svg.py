"""Generates Figma-friendly SVGs of the Schedule Clock watch face.

Usage: python3 design/gen_figma_svg.py   (writes design/watchface-default.svg and design/watchface-selected.svg)

Geometry, colors and type follow RULES.md §2–§4 on the 450 × 450 canvas, using the watch build's sample
day (android/…/SpikeSchedule.kt) at 10:20 AM. Built for editing in Figma: named groups (`id` becomes the
layer name), real text (Anta for the clock and numbers, Roboto standing in for the watch's system font),
plain shapes with inline attributes, no CSS. Private stripes are lines inside a clip path, which Figma
imports as a mask group.
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

AMBER, AMBER_PAST, AMBER_DIM = "#ffb547", "#6e4f22", "#5a4320"
PRIVATE, PRIVATE_DIM = "#8e8e93", "#48484c"
LATER, CLASH, SELECTED = "#8fa3d6", "#ff7a59", "#ffd27f"
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
    """Caution band (R10): 5 px stripes / 5 px gaps at 45° to the arc's middle tangent."""
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


def dial_layers():
    trail = []
    now_deg = deg(NOW)
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


def meetings(dim):
    """The sample day at 10:20 AM (RULES W1–W8): Standup is past; Roadmap is live; Late sync is on the lane."""
    amber = AMBER_DIM if dim else AMBER
    grey = PRIVATE_DIM if dim else PRIVATE
    parts = [
        arc("Roadmap planning · elapsed", 600, NOW, R, AMBER_PAST, 12),
        arc("Roadmap planning · remaining", NOW, 690, R, amber, 12),
        private_arc("Lunch (private)", 750, 780, grey, "clipPrivate" + ("Dim" if dim else "")),
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


def now_dot():
    x, y = pt(deg(NOW), R)
    return (f'<g id="Now dot"><circle cx="{x:.2f}" cy="{y:.2f}" r="11" fill="#ffffff" fill-opacity="0.18"/>'
            f'<circle cx="{x:.2f}" cy="{y:.2f}" r="7" fill="#000000"/>'
            f'<circle cx="{x:.2f}" cy="{y:.2f}" r="4.5" fill="#ffffff"/></g>')


def arrow(x, y, s=1.0):
    """Look-ahead arrow (C12): 20 × 20, 3 px amber, drawn from its left edge x and vertical center y."""
    def ln(x1, y1, x2, y2):
        return (f'<line x1="{x + x1 * s:.2f}" y1="{y + y1 * s:.2f}" x2="{x + x2 * s:.2f}" y2="{y + y2 * s:.2f}" '
                f'stroke="{AMBER}" stroke-width="{3 * s:g}" stroke-linecap="round"/>')
    return f'<g id="Arrow (next)">{ln(1.5, 0, 18.5, 0)}{ln(12, -6.5, 18.5, 0)}{ln(12, 6.5, 18.5, 0)}</g>'


def lock(x, top, s=1.0):
    """Private lock (C13): 16 × 20, grey."""
    return (f'<g id="Lock (private)"><path d="M{x + 3.5 * s:.2f} {top + 9 * s:.2f} V{top + 6.5 * s:.2f} '
            f'a{4.5 * s} {4.5 * s} 0 0 1 {9 * s:.2f} 0 V{top + 9 * s:.2f}" fill="none" stroke="{MUTED}" stroke-width="{2.5 * s:g}"/>'
            f'<rect x="{x:.2f}" y="{top + 9 * s:.2f}" width="{16 * s:g}" height="{12 * s:g}" rx="{2.5 * s:g}" fill="{MUTED}"/></g>')


def overlap_icon(x, y, s=1.0):
    """Double-booking icon (C7): two intersecting rings, 22 px wide, coral."""
    return (f'<g id="Overlap icon"><circle cx="{x + 7 * s:.2f}" cy="{y:.2f}" r="{6 * s:g}" fill="none" stroke="{CLASH}" stroke-width="{2 * s:g}"/>'
            f'<circle cx="{x + 15 * s:.2f}" cy="{y:.2f}" r="{6 * s:g}" fill="none" stroke="{CLASH}" stroke-width="{2 * s:g}"/></g>')


def default_center():
    """C1–C13 default layout (T2): next meeting is the private lunch, with the arrow because Roadmap is live."""
    title_w, extra = 95, 30 + 25          # "Private" at 30 px; arrow + lock space
    icons_x = C - (title_w + extra) / 2
    return ('<g id="Center">'
            + text("Date", C, 122, 24, MUTED, "SUN · OCT 25", weight=500, spacing=2)
            + '<g id="Clock">'
            + text("Time", 203, 206, 88, TEXT, "10:20", family=DISPLAY)
            + text("AM/PM", 322, 206, 24, MUTED, "AM", weight=500, anchor="start") + "</g>"
            + f'<line id="Divider" x1="155" y1="238" x2="295" y2="238" stroke="#2a2a2e" stroke-width="2" stroke-linecap="round"/>'
            + '<g id="Details">'
            + arrow(icons_x, 273.5) + lock(icons_x + 30, 262)
            + text("Title", C + extra / 2, 284, 30, TEXT, "Private", weight=500)
            + text("Time range", C, 318, 24, TEXT, "12:30 – 1:00 PM")
            + text("Place", C, 348, 24, MUTED, "Details hidden")
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


def face(name, center, dim):
    ring = meetings(dim)
    if dim:  # R9: everything else at 35 %, the selected arc on top (22 px, grows inward)
        ring = f'<g id="Dimmed ring" opacity="0.35">{ring}</g>' + arc("Selected arc", 780, 840, R - 5, SELECTED, 22)
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


(OUT / "watchface-default.svg").write_text(face("Schedule Clock · default 10:20 AM", default_center(), dim=False))
(OUT / "watchface-selected.svg").write_text(face("Schedule Clock · selected Design review", selected_center(), dim=True))
print("wrote", OUT / "watchface-default.svg", "and", OUT / "watchface-selected.svg")
