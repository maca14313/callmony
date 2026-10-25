#!/usr/bin/env python3
"""Regenerate every callmony launcher icon from `react-native/the new logo.png`.

v1.6.13 — "you cropped it too much": the previous pass painted the WHOLE logo
square (teal + monogram) across the full 108dp adaptive canvas, so the outer
band was eaten by launcher masks. This pass:

  1. crops the white monogram GLYPH out of the teal square,
  2. centers it at ~43% of the 108dp foreground canvas — fully inside the
     66dp safe-zone circle, so NO mask shape (circle / squircle / rounded
     square / tear) can ever clip it,
  3. paints legacy (pre-8.0) icons as a full-bleed teal square with the glyph
     at ~58% — breathing room on every side,
  4. keeps the iOS 1024 icon a full-bleed teal square with the glyph at 62%
     (Apple masks the corners itself; no alpha allowed),
  5. refreshes the in-app compose resource logo.png (full square, untouched).

Run from the repo root:  python3 scripts/gen_app_icons.py
"""
from PIL import Image, ImageDraw
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "react-native", "the new logo.png")
RES = os.path.join(ROOT, "composeApp/src/androidMain/res")
IOS_ASSETS = os.path.join(ROOT, "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset")
COMPOSE_RES = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/drawable")

TEAL = (7, 141, 125, 255)  # #078b7a — sampled from the logo background

# density → adaptive foreground px (108dp grid)
FG_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
# density → legacy launcher px (48dp icon)
LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def load_parts():
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
    return src, glyph


def paste_centered(canvas: Image.Image, glyph: Image.Image, box: int) -> Image.Image:
    """Scale the glyph to fit `box` px and center it on the canvas."""
    gw, gh = glyph.size
    scale = box / max(gw, gh)
    nw, nh = max(1, round(gw * scale)), max(1, round(gh * scale))
    small = glyph.resize((nw, nh), Image.LANCZOS)
    canvas.alpha_composite(small, ((canvas.width - nw) // 2, (canvas.height - nh) // 2))
    return canvas


def rounded_mask(size: int, radius: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return m


def circle_mask(size: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.ellipse((0, 0, size - 1, size - 1), fill=255)
    return m


def main():
    src, glyph = load_parts()
    print(f"source {src.size}, glyph {glyph.size}")

    # 1. adaptive foregrounds — glyph at 43% of the 108dp canvas
    for dens, size in FG_SIZES.items():
        fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        paste_centered(fg, glyph, round(size * 0.43))
        out = os.path.join(RES, f"mipmap-{dens}", "ic_launcher_foreground.png")
        fg.save(out)
        print(f"  {out} ({size}px, glyph 43%)")

    # 2. legacy launchers — full-bleed teal + glyph at 58%, rounded + circle
    for dens, size in LEGACY_SIZES.items():
        base = Image.new("RGBA", (size, size), TEAL)
        paste_centered(base, glyph, round(size * 0.58))
        square = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        square.paste(base, (0, 0), rounded_mask(size, round(size * 0.18)))
        square.save(os.path.join(RES, f"mipmap-{dens}", "ic_launcher.png"))
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(base, (0, 0), circle_mask(size))
        round_icon.save(os.path.join(RES, f"mipmap-{dens}", "ic_launcher_round.png"))
        print(f"  mipmap-{dens}/ic_launcher(_round).png ({size}px, glyph 58%)")

    # 3. iOS 1024 — full-bleed teal + glyph 62%, RGB (no alpha)
    ios = Image.new("RGBA", (1024, 1024), TEAL)
    paste_centered(ios, glyph, round(1024 * 0.62))
    ios.convert("RGB").save(os.path.join(IOS_ASSETS, "AppIcon-1024.png"))
    print(f"  {IOS_ASSETS}/AppIcon-1024.png (glyph 62%)")

    # 4. in-app logo resource — the full artwork on a SQUARE teal canvas
    #    (the app shows it in square rounded tiles; padding keeps the whole
    #    glyph visible instead of cropping the taller side)
    side = 640
    scale = side / max(src.width, src.height)
    nw, nh = round(src.width * scale), round(src.height * scale)
    scaled = src.resize((nw, nh), Image.LANCZOS)
    inapp = Image.new("RGBA", (side, side), TEAL)
    inapp.alpha_composite(scaled, ((side - nw) // 2, (side - nh) // 2))
    inapp.convert("RGB").save(os.path.join(COMPOSE_RES, "logo.png"))
    print(f"  {COMPOSE_RES}/logo.png (square 640px, artwork padded)")

    print("done")


if __name__ == "__main__":
    main()
