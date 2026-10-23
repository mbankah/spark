# Generates every Spark logo file from one flame path: brand SVGs, the Android adaptive icon, and the installer mark.
# Run from the efa folder: python tools/gen-logo.py
# The flame is one shape with a smaller flame cut out of it (even-odd fill), so it sits on any surface.
import re

OUTER = "M256 52 C268 140 388 186 388 322 C388 404 330 462 256 462 C182 462 124 404 124 322 C124 262 152 222 186 186 C190 224 206 244 228 250 C214 176 232 108 256 52 Z"
INNER = "M256 262 C262 312 320 332 320 388 C320 426 292 450 256 450 C220 450 192 426 192 388 C192 346 230 330 256 262 Z"
FLAME = OUTER + " " + INNER

# Cold palette from the app. accent = flame, bg = tile.
# top/bottom are the gradient ends (tip to base). The gradient is for the app icon and brand files only: the ledger allows no
# gradients inside the app's interface except the dial ring, so the in-app mark and the installer use the flat accent.
THEMES = {
    "dark":  {"bg": "#0c1218", "accent": "#3ec6e0", "top": "#9cf3ff", "bottom": "#1f8fd0"},
    "light": {"bg": "#e3e9ef", "accent": "#1f6feb", "top": "#62b0ff", "bottom": "#1a3fc4"},
    "night": {"bg": "#0a0604", "accent": "#c4501f", "top": "#e8863a", "bottom": "#8f3412"},
}


def svg_icon(name, t):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" role="img" aria-label="Spark">
  <title>Spark</title>
  <!-- Cold {name} palette from the app. The inner flame is cut out, so the tile shows through. -->
  <defs>
    <linearGradient id="flame" gradientUnits="userSpaceOnUse" x1="0" y1="52" x2="0" y2="462">
      <stop offset="0" stop-color="{t['top']}"/>
      <stop offset="1" stop-color="{t['bottom']}"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" rx="112" fill="{t['bg']}"/>
  <path fill-rule="evenodd" fill="url(#flame)" d="{FLAME}"/>
</svg>
"""


def to_adaptive(path, scale=0.112, cx=54.0, cy=54.0):
    """Maps the 512 unit flame into the 108 dp adaptive-icon canvas, inside the 66 dp safe zone."""
    toks = re.findall(r"[MCZ]|-?\d+(?:\.\d+)?", path)
    out, nums, cmd = [], [], None

    def flush():
        pass
    i = 0
    res = []
    while i < len(toks):
        t = toks[i]
        if t in "MCZ":
            res.append(t); i += 1
            continue
        x = float(toks[i]); y = float(toks[i + 1]); i += 2
        res.append(f"{cx + (x - 256) * scale:.2f},{cy + (y - 257) * scale:.2f}")
    return " ".join(res).replace("M ", "M").replace("C ", "C").replace(" Z", "Z")


def vector(top, bottom=None, comment=""):
    """A vector layer. With a bottom colour the flame has a vertical gradient from tip to base; without, it is one solid colour."""
    d = f"{to_adaptive(OUTER)} {to_adaptive(INNER)}"
    y0 = 54.0 + (52 - 257) * 0.112
    y1 = 54.0 + (462 - 257) * 0.112
    if bottom is None:
        fill = f'android:fillColor="{top}"'
        return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="{d}"
        android:fillType="evenOdd" {fill}/>
</vector>
"""
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="{d}" android:fillType="evenOdd">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="{y0:.2f}" android:endX="54" android:endY="{y1:.2f}"
                android:startColor="{top}" android:endColor="{bottom}"/>
        </aapt:attr>
    </path>
</vector>
"""


def w(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


w("brand/spark-icon.svg", svg_icon("dark", THEMES["dark"]))
w("brand/spark-icon-light.svg", svg_icon("light", THEMES["light"]))
w("brand/spark-icon-night.svg", svg_icon("night", THEMES["night"]))
w("brand/spark-wordmark.svg", f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 960 256" role="img" aria-label="Spark">
  <title>Spark</title>
  <defs>
    <linearGradient id="flame" gradientUnits="userSpaceOnUse" x1="0" y1="52" x2="0" y2="462">
      <stop offset="0" stop-color="{THEMES['dark']['top']}"/>
      <stop offset="1" stop-color="{THEMES['dark']['bottom']}"/>
    </linearGradient>
  </defs>
  <g transform="scale(0.5)">
    <rect width="512" height="512" rx="112" fill="{THEMES['dark']['bg']}"/>
    <path fill-rule="evenodd" fill="url(#flame)" d="{FLAME}"/>
  </g>
  <text x="300" y="168" font-family="DM Sans, Roboto, Arial, sans-serif" font-size="150" font-weight="600" fill="#14202b" letter-spacing="-3">Spark</text>
</svg>
""")

# Android adaptive icon: foreground per palette, background colour per palette, themed (monochrome) layer.
w("app/src/main/res/drawable/ic_launcher_foreground.xml", vector(THEMES["light"]["top"], THEMES["light"]["bottom"], comment="Spark flame with a tip-to-base gradient, Cold light palette. drawable-night has the dark one. Inside the 66 dp safe zone."))
w("app/src/main/res/drawable-night/ic_launcher_foreground.xml", vector(THEMES["dark"]["top"], THEMES["dark"]["bottom"], comment="Spark flame with a tip-to-base gradient, Cold dark palette, used when the phone is in dark mode."))
w("app/src/main/res/drawable/ic_launcher_monochrome.xml", vector("#000000", None, comment="Themed-icon layer: one colour, the flame with its inner flame cut out."))

# In-app mark and installer use the same path.
print("outer for Kotlin:", OUTER)
print("inner for Kotlin:", INNER)
print(to_adaptive(OUTER)[:80])
