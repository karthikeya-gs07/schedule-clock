"""Draws the Play Store graphics for Calen-Time (PRD §6.5, phase 4): the 512 px icon and the 1024 × 500 feature graphic.
Same design as the launcher icon (android/*/src/main/res/drawable/ic_launcher_foreground.xml): the ring with three
meetings and the now dot. Run: python3 store/make_assets.py  (needs Pillow)."""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
BG, TRACK, AMBER, WHITE = (11, 11, 13), (42, 43, 48), (255, 181, 71), (255, 255, 255)
ARCS, NOW = [(0.35, 1.9), (3.4, 4.7), (6.2, 7.5)], 11.3

def ring(draw, cx, cy, r, w):
    box = [cx - r, cy - r, cx + r, cy + r]
    draw.arc(box, 0, 360, fill=TRACK, width=w)
    for a, b in ARCS:
        draw.arc(box, a * 30 - 90, b * 30 - 90, fill=AMBER, width=w)
        for h in (a, b):   # round caps
            x, y = cx + (r - w / 2) * math.sin(math.radians(h * 30)), cy - (r - w / 2) * math.cos(math.radians(h * 30))
            draw.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=AMBER)
    x, y = cx + (r - w / 2) * math.sin(math.radians(NOW * 30)), cy - (r - w / 2) * math.cos(math.radians(NOW * 30))
    draw.ellipse([x - w * .83, y - w * .83, x + w * .83, y + w * .83], fill=(0, 0, 0))
    draw.ellipse([x - w * .55, y - w * .55, x + w * .55, y + w * .55], fill=WHITE)

S = 4   # supersampling
icon = Image.new("RGB", (512 * S, 512 * S), BG)
ring(ImageDraw.Draw(icon), 256 * S, 256 * S, int(170 * S), int(42 * S))   # fills the square more than the launcher safe zone
icon.resize((512, 512), Image.LANCZOS).save(OUT / "icon-512.png")

feature = Image.new("RGB", (1024 * S, 500 * S), BG)
d = ImageDraw.Draw(feature)
ring(d, 250 * S, 250 * S, 170 * S, 34 * S)
font_path = Path(__file__).parents[1] / "prototype/anta.ttf"
d.text((470 * S, 160 * S), "Calen-Time", font=ImageFont.truetype(str(font_path), 92 * S), fill=WHITE)
d.text((474 * S, 290 * S), "Your day, around the clock.", font=ImageFont.truetype(str(font_path), 40 * S), fill=(142, 142, 147))
feature.resize((1024, 500), Image.LANCZOS).save(OUT / "feature-1024x500.png")
print("wrote", OUT / "icon-512.png", "and", OUT / "feature-1024x500.png")
