#!/usr/bin/env python3
"""Generates Aetherion's nine-slice GUI sprites (anti-aliased rounded rects, hairline rings, soft glows).

Pure standard library (zlib/struct). Output: src/main/resources/assets/aetherion/textures/gui/sprites/ui/
Run from the repo root:  python tools/gen_sprites.py
Textures are supersampled (SS texels per logical GUI unit) so corners stay smooth at any GUI scale.
"""
import json, math, os, struct, zlib

SS = 4
OUT = os.path.join("src", "main", "resources", "assets", "aetherion", "textures", "gui", "sprites", "ui")
RADII = [2, 3, 4, 5, 6, 8, 10, 12, 14, 16, 20]


def write_png(path, w, h, alpha):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes((255, 255, 255, alpha[y * w + x]))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def mcmeta(path, logical, border):
    with open(path + ".mcmeta", "w") as f:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": logical, "height": logical, "border": border}}}, f)


def sd_round_box(px, py, half, radius):
    """Signed distance (logical units) to a rounded square centred at origin with half-extent `half`."""
    qx, qy = abs(px) - (half - radius), abs(py) - (half - radius)
    outside = math.hypot(max(qx, 0.0), max(qy, 0.0))
    return outside + min(max(qx, qy), 0.0) - radius


def coverage(d):
    # 1.25 texel wide smoothstep: soft enough to survive nearest-neighbour minification without crawling.
    t = max(0.0, min(1.0, 0.5 - d * SS / 1.25))
    return t * t * (3 - 2 * t)


def fill(c):
    logical = 2 * c + 2
    n = logical * SS
    a = []
    for y in range(n):
        for x in range(n):
            d = sd_round_box((x + 0.5) / SS - logical / 2, (y + 0.5) / SS - logical / 2, logical / 2, c)
            a.append(round(255 * coverage(d)))
    return logical, n, a


def ring(c, thickness=0.6):
    logical, n, _ = fill(c)
    a = []
    for y in range(n):
        for x in range(n):
            px, py = (x + 0.5) / SS - logical / 2, (y + 0.5) / SS - logical / 2
            outer = coverage(sd_round_box(px, py, logical / 2, c))
            inner = coverage(sd_round_box(px, py, logical / 2 - thickness, max(c - thickness, 0.0)))
            a.append(round(255 * max(0.0, outer - inner)))
    return logical, n, a


def glow(extent, radius, sigma, peak):
    """Gaussian-blurred rounded rect. Drawn at (x-extent, y-extent, w+2*extent, h+2*extent)."""
    border = extent + radius
    logical = 2 * border + 2
    n = logical * SS
    # Separable gaussian over the analytic coverage of the sharp rounded box.
    sharp = []
    inner_half = logical / 2 - extent
    for y in range(n):
        for x in range(n):
            d = sd_round_box((x + 0.5) / SS - logical / 2, (y + 0.5) / SS - logical / 2, inner_half, radius)
            sharp.append(1.0 if d < 0 else 0.0)
    k = int(sigma * SS * 3)
    kern = [math.exp(-(i * i) / (2 * (sigma * SS) ** 2)) for i in range(-k, k + 1)]
    s = sum(kern); kern = [v / s for v in kern]
    def blur(src, horizontal):
        dst = [0.0] * (n * n)
        for y in range(n):
            for x in range(n):
                acc = 0.0
                for i, w in enumerate(kern):
                    o = i - k
                    xx, yy = (x + o, y) if horizontal else (x, y + o)
                    if 0 <= xx < n and 0 <= yy < n:
                        acc += src[yy * n + xx] * w
                dst[y * n + x] = acc
        return dst
    out = blur(blur(sharp, True), False)
    return logical, border, n, [round(255 * peak * v) for v in out]


def main():
    os.makedirs(OUT, exist_ok=True)
    for c in RADII:
        logical, n, a = fill(c); p = os.path.join(OUT, f"r{c}.png"); write_png(p, n, n, a); mcmeta(p, logical, c)
        logical, n, a = ring(c); p = os.path.join(OUT, f"b{c}.png"); write_png(p, n, n, a); mcmeta(p, logical, c)
    for name, extent, radius, sigma in (("glow", 14, 10, 5.0), ("glow_s", 8, 6, 2.8)):
        logical, border, n, a = glow(extent, radius, sigma, 1.0)
        p = os.path.join(OUT, f"{name}.png"); write_png(p, n, n, a); mcmeta(p, logical, border)
    print("sprites written to", OUT)


if __name__ == "__main__":
    main()
