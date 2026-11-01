"""Draws the app's generic watch body (no brand): android/phone/src/main/res/drawable-nodpi/watch_body.png.
Canvas matches the design box 334.5 x 498.8, with the 234.3 round face centred in it."""
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
    t = min(1.0, max(0.0, (145 - abs(dy)) / 40))
    return base + flare * t * t
for y in range(H):
    dy = (y - cy) / k
    w = half(dy, 66, 17) * k
    d.line([cx - w, y, cx + w, y], fill=(14, 14, 15, 255))
    w = half(dy, 46, 0) * k
    d.line([cx - w, y, cx + w, y], fill=(19, 19, 20, 255))
# Crown and side button
rr(290, 208, 304, 246, 3, (38, 38, 41, 255))
rr(290, 262, 303.5, 292, 3, (30, 30, 33, 255))
# Case, edge highlight, bezel, glass
for r, c in ((134, (120, 120, 128, 255)), (131, (58, 58, 63, 255)), (124, (12, 12, 13, 255)), (119, (0, 0, 0, 255))):
    d.ellipse([cx - r * k, cy - r * k, cx + r * k, cy + r * k], fill=c)
im.resize((W // 2, H // 2), Image.LANCZOS).save("android/phone/src/main/res/drawable-nodpi/watch_body.png")
