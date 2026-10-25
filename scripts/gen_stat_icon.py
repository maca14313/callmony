#!/usr/bin/env python3
"""v1.6.16 — the white monogram STATUS-BAR icon (ic_stat_callmony).

The old drawable was the legacy phone-in-talk vector — the "old logo" the
user kept seeing in the notification shade. This generates a flat
WHITE-ON-TRANSPARENT PNG of the new logo's monogram glyph (status-bar icons
must be pure alpha masks — Android renders them white regardless), which
replaces the vector everywhere (notification small icon + action icons
keep their own assets)."""
from PIL import Image
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "react-native", "the new logo.png")
OUT = os.path.join(ROOT, "composeApp/src/androidMain/res/drawable-nodpi/ic_stat_callmony.png")

src = Image.open(SRC).convert("RGBA")
w, h = src.size
corner = src.getpixel((4, 4))
px = src.load()
minx, miny, maxx, maxy = w, h, 0, 0
for y in range(0, h, 2):
    for x in range(0, w, 2):
        r, g, b, a = px[x, y]
        if abs(r - corner[0]) + abs(g - corner[1]) + abs(b - corner[2]) > 60:
            if x < minx: minx = x
            if x > maxx: maxx = x
            if y < miny: miny = y
            if y > maxy: maxy = y
glyph = src.crop((minx, miny, maxx + 1, maxy + 1))

SIZE = 96  # rendered ~24dp — plenty of headroom for xxxhdpi
gw, gh = glyph.size
scale = (SIZE * 0.78) / max(gw, gh)  # breathing room inside the safe zone
nw, nh = max(1, round(gw * scale)), max(1, round(gh * scale))
small = glyph.resize((nw, nh), Image.LANCZOS)

# alpha-only mask: every opaque glyph pixel becomes pure WHITE
mask = small.split()[3]
white = Image.new("RGBA", (nw, nh), (255, 255, 255, 0))
white.putalpha(mask)

canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
canvas.alpha_composite(white, ((SIZE - nw) // 2, (SIZE - nh) // 2))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
canvas.save(OUT)
print("wrote", OUT, canvas.size)
