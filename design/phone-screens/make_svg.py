"""Builds editable SVGs of the CalenTime phone screens (Fold7 cover, 1080 x 2520) from emulator captures:
text from the UI dump as live <text>, shapes and the app's own icon paths as vectors, the watch face as an image."""
import base64, io, sys, xml.etree.ElementTree as ET
from html import escape
from PIL import Image, ImageFont

D = sys.argv[1]; OUT = sys.argv[2]
FONT = sys.argv[3]
W, H = 1080, 2520
ACCENT = '#FFB547'; DIM = '#8E8E93'

ICONS = {
    'home': ['M12 3 3 10v11h6v-6h6v6h6V10z'],
    'calendar': ['M6.5 5h11a3 3 0 0 1 3 3v9.5a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z', 'M3.5 10h17M8 3v4M16 3v4'],
    'look': ['M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z'], 'look_fill': 'M12 3.5a8.5 8.5 0 0 1 0 17z',
    'settings': ['M4 7h8.5M17.5 7H20M4 17h2.5M11.5 17H20', 'M15 4.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z', 'M9 14.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z'],
    'sync': ['M20 12a8 8 0 0 1-14.3 4.9M4 12A8 8 0 0 1 18.3 7.1M18.5 3v4.3h-4.3M5.5 21v-4.3h4.3'],
    'check': ['m5 12.5 4.5 4.5L19 7.5'],
    'lock': ['M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 18v-5a2.5 2.5 0 0 1 2.5-2.5z', 'M8 10.5V8a4 4 0 0 1 8 0v2.5'],
    'buzz': ['M10.5 4h3A2.5 2.5 0 0 1 16 6.5v11a2.5 2.5 0 0 1-2.5 2.5h-3A2.5 2.5 0 0 1 8 17.5v-11A2.5 2.5 0 0 1 10.5 4z', 'M4.5 9v6M19.5 9v6'],
    'watch': ['M12 6a6 6 0 1 1 0 12a6 6 0 1 1 0-12z', 'M9 6.5 9.6 3h4.8l.6 3.5M9 17.5l.6 3.5h4.8l.6-3.5M12 9.5V12l1.6 1.2'],
    'chevron': ['m6 9 6 6 6-6'], 'close': ['M6 6l12 12M18 6 6 18'],
    'clock': ['M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z', 'M12 7.5V12l3 2'],
    'info': ['M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z', 'M12 11v5.5M12 7.8v.1'],
}

def icon(name, cx, cy, size, colour, id_=None):
    s = size / 24
    out = [f'<g id="{id_ or "icon-" + name}" transform="translate({cx - size/2:.1f} {cy - size/2:.1f}) scale({s:.4f})" fill="none" stroke="{colour}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">']
    out += [f'<path d="{p}"/>' for p in ICONS[name]]
    if name == 'look': out.append(f'<path d="{ICONS["look_fill"]}" fill="{colour}" stroke="none"/>')
    return ''.join(out) + '</g>'

def bounds(b):
    a, c = b.split('][')
    x1, y1 = map(int, a[1:].split(',')); x2, y2 = map(int, c[:-1].split(','))
    return x1, y1, x2, y2

def hexc(rgb): return '#%02X%02X%02X' % rgb[:3]

def text_colour(im, x1, y1, x2, y2):
    """The text's colour: the brightest-ish pixel in its box (text is lighter than the dark background)."""
    px = [im.getpixel((x, y)) for x in range(x1, min(x2, W - 1), 2) for y in range(y1, min(y2, H - 1), 2)]
    px.sort(key=lambda p: sum(p))
    return px[int(len(px) * .985)] if px else (255, 255, 255)

fonts = {}
def font(size):
    if size not in fonts: fonts[size] = ImageFont.truetype(FONT, size)
    return fonts[size]

def wrap(text, size, width):
    words, lines, cur = text.split(' '), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if font(size).getlength(t) <= width * 1.02 or not cur: cur = t
        else: lines.append(cur); cur = w
    return lines + [cur]

def text_el(t, x1, y1, x2, y2, colour, align='start', weight=None, id_=None):
    h = y2 - y1; w = x2 - x1
    # Font size: the size whose wrapped lines fill the box at a 1.25-1.5 line height.
    # The app's own type scale (sp size, sp line height; Ui.kt / Home.kt), at 2.625 px per sp (420 dpi).
    best = None
    for sp, lsp in [(13, 18), (14, 20), (15, 21), (16, 22), (16, 23), (17, 23), (19, 24), (19, 25), (28, 34), (32, 40), (36, 44)]:
        fs = round(sp * 2.625); lpx = lsp * 2.625
        for n in range(1, 9):
            if abs(h / n - lpx) > 9: continue
            lines = wrap(t, fs, w)
            if len(lines) != n: continue
            fit = abs(h / n - lpx) + (abs(font(fs).getlength(t) - w) / 40 if n == 1 else 0)
            if best is None or fit < best[0]: best = (fit, fs, lines, h / n)
    if best is None:
        fs = round(h * 0.7); best = (0, fs, [t], h)
    _, fs, lines, lh = best
    if weight is None: weight = 500 if (fs >= 50 or sum(colour) > 690) else 400
    x = {'start': x1, 'middle': (x1 + x2) / 2, 'end': x2}[align]
    base = y1 + (lh - fs) / 2 + fs * 0.80
    spans = ''.join(f'<tspan x="{x:.0f}" y="{base + i * lh:.0f}">{escape(l)}</tspan>' for i, l in enumerate(lines))
    return (f'<text id="{escape(id_ or "text-" + t[:24])}" font-family="Google Sans Flex, Google Sans, sans-serif" '
            f'font-size="{fs}" font-weight="{weight}" fill="{hexc(colour)}" text-anchor="{align}">{spans}</text>')

def image(im, box, id_, clip_circle=False, radius=0):
    crop = im.crop(box); buf = io.BytesIO(); crop.save(buf, 'PNG')
    x1, y1, x2, y2 = box
    data = base64.b64encode(buf.getvalue()).decode()
    clip = ''
    if clip_circle or radius:
        cid = id_ + '-clip'
        shape = (f'<circle cx="{(x1+x2)/2}" cy="{(y1+y2)/2}" r="{(x2-x1)/2}"/>' if clip_circle else
                 f'<rect x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" rx="{radius}"/>')
        clip = f'<clipPath id="{cid}">{shape}</clipPath>'
        return f'{clip}<image id="{id_}" clip-path="url(#{cid})" x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" href="data:image/png;base64,{data}"/>'
    # Feathered edges so the photo melts into the editable background behind it.
    m = id_ + '-fade'
    fade = (f'<defs><linearGradient id="{m}-h"><stop offset="0" stop-color="#000"/><stop offset=".12" stop-color="#fff"/><stop offset=".88" stop-color="#fff"/><stop offset="1" stop-color="#000"/></linearGradient>'
            f'<linearGradient id="{m}-v" x2="0" y2="1"><stop offset="0" stop-color="#000"/><stop offset=".1" stop-color="#fff"/><stop offset=".9" stop-color="#fff"/><stop offset="1" stop-color="#000"/></linearGradient>'
            f'<mask id="{m}-v-mask"><rect x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" fill="url(#{m}-v)"/></mask>'
            f'<mask id="{m}"><rect x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" fill="url(#{m}-h)" mask="url(#{m}-v-mask)"/></mask></defs>')
    return fade + f'<image id="{id_}" mask="url(#{m})" x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" href="data:image/png;base64,{data}"/>'

FROST = 'fill="#3A3A3E" fill-opacity="0.55" stroke="#FFFFFF" stroke-opacity="0.08" stroke-width="2.6"'

def circle_btn(cx, cy, name, colour='#FFFFFF', r=75, id_=None):
    return f'<g id="{id_ or "button-" + name}"><circle cx="{cx}" cy="{cy}" r="{r}" {FROST}/>{icon(name, cx, cy, 63, colour)}</g>'

def round_icon(cx, cy, name):   # the small frosted icon beside list items
    return f'<g id="item-icon-{name}"><circle cx="{cx}" cy="{cy}" r="49" {FROST}/>{icon(name, cx, cy, 44, "#F0F0F0")}</g>'

def toggle(x, y, on, id_):
    if on: return f'<g id="{id_}"><rect x="{x}" y="{y}" width="137" height="84" rx="42" fill="{ACCENT}"/><circle cx="{x+95}" cy="{y+42}" r="32" fill="#1A1206"/></g>'
    return f'<g id="{id_}"><rect x="{x}" y="{y}" width="137" height="84" rx="42" fill="#34353A"/><circle cx="{x+42}" cy="{y+42}" r="21" fill="#8E8E93"/></g>'

def dot(cx, cy, colour, r=13): return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{colour}"/>'
def divider(y, x1=53, x2=1027): return f'<rect id="divider" x="{x1}" y="{y}" width="{x2-x1}" height="2.6" fill="#FFFFFF" fill-opacity="0.07"/>'

def status_bar(time='6:26'):
    return ('<g id="status-bar">'
            f'<text x="44" y="83" font-family="Google Sans Flex, Google Sans, sans-serif" font-size="37" fill="#F2F2F2">{time}</text>'
            '<path d="M155 56l15 6v9c0 9-6 16-15 19c-9-3-15-10-15-19v-9z" fill="none" stroke="#F2F2F2" stroke-width="4"/>'
            '<g fill="#F2F2F2"><rect x="815" y="74" width="7" height="12" rx="2"/><rect x="827" y="67" width="7" height="19" rx="2"/>'
            '<rect x="839" y="60" width="7" height="26" rx="2"/><rect x="851" y="53" width="7" height="33" rx="2" fill-opacity=".35"/></g>'
            '<path d="M876 66a32 32 0 0 1 44 0M884 74a20 20 0 0 1 28 0" fill="none" stroke="#F2F2F2" stroke-width="6" stroke-linecap="round"/><circle cx="898" cy="82" r="5" fill="#F2F2F2"/>'
            '<rect x="935" y="54" width="62" height="34" rx="12" fill="#F2F2F2"/><rect x="1000" y="64" width="5" height="14" rx="2" fill="#F2F2F2"/>'
            '</g>')

def nav(active):
    out = ['<g id="navigation">', f'<rect x="104" y="2173" width="872" height="210" rx="105" fill="#2C2C2E" fill-opacity="0.92" stroke="#FFFFFF" stroke-opacity="0.08" stroke-width="2.6"/>']
    for i, n in enumerate(['home', 'look', 'calendar', 'settings']):
        out.append(icon(n, 237 + 202 * i, 2278, 63, ACCENT if n == active else DIM, id_='nav-' + n))
    return ''.join(out) + '</g>'

HOME_BAR = '<rect id="home-indicator" x="398" y="2479" width="284" height="11" rx="5.5" fill="#FFFFFF"/>'

def background(im, glow_y=None, glow_colour=None):
    top = hexc(im.getpixel((540, 150))); bot = hexc(im.getpixel((540, 2450)))
    out = ['<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
           f'<stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bot}"/></linearGradient>']
    if glow_y is not None:
        out.append(f'<radialGradient id="glow" cx="540" cy="{glow_y}" r="900" gradientUnits="userSpaceOnUse">'
                   f'<stop offset="0" stop-color="{glow_colour}" stop-opacity="0.45"/><stop offset="1" stop-color="{glow_colour}" stop-opacity="0"/></radialGradient>')
    out.append('</defs><rect id="background" width="1080" height="2520" fill="url(#bg)"/>')
    if glow_y is not None: out.append('<rect id="colourway-glow" width="1080" height="2520" fill="url(#glow)"/>')
    return ''.join(out)

def texts(im, root, skip=(), align=None, weights=None, exclude_rect=None):
    out = []
    for e in root.iter('node'):
        t = e.attrib.get('text')
        if not t or t in skip or e.attrib['class'].endswith(('WebView', 'Image')): continue
        x1, y1, x2, y2 = bounds(e.attrib['bounds'])
        if exclude_rect and not (y2 <= exclude_rect[1] or y1 >= exclude_rect[3]): continue
        a = (align or {}).get(t, 'start')
        out.append(text_el(t, x1, y1, x2, y2, text_colour(im, x1, y1, x2, y2), a, (weights or {}).get(t)))
    return out

def svg(name, parts):
    body = '\n'.join(parts)
    open(f'{OUT}/{name}.svg', 'w', encoding='utf-8').write(
        f'<?xml version="1.0" encoding="UTF-8"?>\n<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">\n'
        f'<title>CalenTime · {name} · Galaxy Z Fold cover screen 1080 × 2520</title>\n{body}\n</svg>\n')

def load(n): return Image.open(f'{D}/{n}.png').convert('RGB'), ET.parse(f'{D}/{n}.xml').getroot()

# ---------- Today ----------
im, root = load('today')
p = [background(im, 1350, '#6B4A1E'), image(im, (170, 100, 910, 1290), 'watch-and-face'), status_bar('6:26')]
p += [circle_btn(160, 333, 'clock', id_='watch-status'), dot(189, 306, '#5FD4A0', 10),
      circle_btn(920, 333, 'info', id_='about-button'),
      '<g id="sync-pill"><rect x="201" y="1311" width="480" height="150" rx="75" ' + FROST + '/></g>',
      circle_btn(805, 1386, 'sync', id_='sync-button')]
p += ['<g id="drawer"><path d="M0 1636a100 100 0 0 1 100-100h880a100 100 0 0 1 100 100V2520H0z" fill="#141312" fill-opacity="0.96"/>'
      '<rect x="493" y="1573" width="95" height="11" rx="5.5" fill="#FFFFFF" fill-opacity="0.35"/>']
p += [dot(87, 1848, '#4F8FF7'), dot(87, 2073, '#5FD4A0'), dot(87, 2297, '#4F8FF7'), divider(1991, 74, 1006), divider(2216, 74, 1006)]
right = {k: 'end' for k in ['7 meetings', '10:45 AM', '11:45 AM', '12:30 PM', '1:30 PM']}
p += texts(im, root, skip={'Teams', '2:00 PM', '2:45 PM', 'Synced 2 minutes ago', 'Galaxy Watch8 · Connected'}, align=right)
p += ['</g>']
p += [text_el('Synced 2 minutes ago', 259, 1337, 624, 1390, (242, 242, 242), 'middle', 500),
      text_el('Galaxy Watch8 · Connected', 253, 1390, 630, 1436, (160, 160, 165), 'middle', 400)]
p += [nav('home'), HOME_BAR]
svg('1-today', p)

# ---------- Watchface ----------
im, root = load('watchface')
p = [background(im, 1100, '#6B4A1E'), image(im, (170, 380, 910, 1650), 'watch-and-face'), status_bar('6:27')]
p += ['<g id="segmented-control"><rect x="236" y="216" width="608" height="115" rx="57.5" fill="#2A2A2C" stroke="#FFFFFF" stroke-opacity="0.08" stroke-width="2.6"/>'
      '<rect x="247" y="227" width="294" height="93" rx="46.5" fill="#3A3A3E"/></g>']
dots = ['<g id="page-dots"><rect x="350" y="1685" width="47" height="17" rx="8.5" fill="' + ACCENT + '"/>']
dots += [dot(426 + 37 * i, 1693, '#5A5A5E', 8) for i in range(9)]
p += dots + ['</g>']
p += ['<g id="on-your-watch-button"><rect x="299" y="1973" width="483" height="137" rx="68.5" ' + FROST + '/>' + icon('check', 397, 2041, 45, '#F0F0F0') + '</g>']
p += texts(im, root, align={'Solids': 'middle', 'Gradients': 'middle', 'Amber': 'middle', 'Warm amber arcs, the original CalenTime.': 'middle'})
p += [nav('look'), HOME_BAR]
svg('2-watchface', p)

# ---------- Calendars ----------
im, root = load('calendars')
p = [background(im), status_bar('6:27')]
for cy, col in [(609, '#5FD4A0'), (757, '#9AA0A6'), (905, '#B48CFF'), (1315, '#4F8FF7')]:
    p.append(f'<g id="calendar-colour"><circle cx="103" cy="{cy}" r="49" {FROST}/>{dot(103, cy, col, 19)}</g>')
p += [toggle(890, 567, True, 'switch-personal'), toggle(890, 715, False, 'switch-holidays'), toggle(890, 863, True, 'switch-family'),
      toggle(890, 1273, True, 'switch-outlook-calendar'), divider(1000), divider(1410), round_icon(103, 1680, 'info'), icon('chevron', 1000, 1680, 45, '#F0F0F0')]
p += texts(im, root)
p += [nav('calendar'), HOME_BAR]
svg('3-calendars', p)

# ---------- Settings ----------
im, root = load('settings')
p = [background(im), status_bar('6:27')]
for cy, ic, ty in [(563, 'buzz', 521), (753, 'clock', 712), (971, 'buzz', 930), (1189, 'watch', 1148)]:
    p += [round_icon(103, cy, ic), toggle(890, ty, True, 'switch-' + ic)]
p += [round_icon(103, 1556, 'sync'), round_icon(103, 1831, 'lock'), divider(1320), divider(1971)]
p += ['<g id="run-setup-again"><rect x="53" y="2305" width="974" height="137" rx="68.5" ' + FROST + '/>' + icon('sync', 372, 2373, 45, '#F0F0F0') + '</g>']
p += texts(im, root, skip={'Run setup again'})
p += [text_el('Run setup again', 423, 2345, 731, 2402, (242, 242, 242), 'start', 500)]
p += [nav('settings'), HOME_BAR]
svg('4-settings', p)

# ---------- About ----------
im, root = load('about')
p = [background(im, 0, '#8A6A30'), status_bar('6:27'), circle_btn(952, 333, 'close', id_='close-button')]
p += [image(im, (53, 1007, 1028, 1555), 'face-closeup-arcs', radius=60), image(im, (53, 1883, 1028, 2431), 'face-closeup-dot', radius=60), divider(1822)]
p += texts(im, root, skip={'The dot'})
p += [text_el('The dot', 53, 2468, 200, 2520, (242, 242, 242), 'start', 500), HOME_BAR]
svg('5-about', p)
print('ok')
