"""Draws the app's generic watch body (no brand): android/phone/src/main/res/drawable-nodpi/watch_body.png.
Canvas matches the design box 334.5 x 498.8, with the 234.3 round face centred in it."""
import math
from PIL import Image, ImageDraw
W, H = 1024 * 2, 1527 * 2
k = W / 334.5
cx, cy = W / 2, H / 2
im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
def rr(x0, y0, x1, y1, r, fill): d.rounded_rectangle([x0 * k, y0 * k, x1 * k, y1 * k], r * k, fill=fill)
# Strap, top and bottom, with a soft centre band
# Each row's half width: narrow, flaring in a smooth curve into the case.
def half(dy, base, flare):
    t = min(1.0, max(0.0, (150 - abs(dy)) / 34))
    return base + flare * t * t
for y in range(H):
    dy = (y - cy) / k
    w = half(dy, 66, 17) * k
    d.line([cx - w, y, cx + w, y], fill=(14, 14, 15, 255))
    w = half(dy, 46, 0) * k
    d.line([cx - w, y, cx + w, y], fill=(19, 19, 20, 255))
# Crown and side button
rr(288, 208, 300.5, 246, 3, (38, 38, 41, 255))
rr(288, 262, 300, 292, 3, (30, 30, 33, 255))
# Case, edge highlight, bezel, glass
# (a squircle, gently; the glass stays round for the round face)
def squircle(r, n=3.0):
    pts = []
    for i in range(720):
        t = math.radians(i / 2)
        c, q = math.cos(t), math.sin(t)
        pts.append((cx + r * k * math.copysign(abs(c) ** (2 / n), c), cy + r * k * math.copysign(abs(q) ** (2 / n), q)))
    return pts
for r, c in ((131, (120, 120, 128, 255)), (128, (58, 58, 63, 255)), (123, (12, 12, 13, 255))):
    d.polygon(squircle(r), fill=c)
r = 119
d.ellipse([cx - r * k, cy - r * k, cx + r * k, cy + r * k], fill=(0, 0, 0, 255))
im.resize((W // 2, H // 2), Image.LANCZOS).save("android/phone/src/main/res/drawable-nodpi/watch_body.png")
