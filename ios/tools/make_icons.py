"""iOS ilova ikonalari (1024x1024) — Android'dagi ic_launcher_fg chizmalari asosida.

Ishlatish: python ios/tools/make_icons.py ios/App/Assets.xcassets
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1]
SS = 2                      # silliq chiziqlar uchun 2x katta chizib, keyin kichraytiramiz
N = 1024 * SS
CROP0, CROP = 12.0, 84.0    # Android 108-birlik maydonining markaziy qismi -> butun ikona


def P(x, y):
    s = N / CROP
    return ((x - CROP0) * s, (y - CROP0) * s)


def cubic(p0, p1, p2, p3, steps=48):
    pts = []
    for i in range(1, steps + 1):
        t = i / steps
        a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t ** 2, t ** 3
        pts.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return pts


def save(img, name):
    d = os.path.join(OUT, name)
    os.makedirs(d, exist_ok=True)
    img.resize((1024, 1024), Image.LANCZOS).convert("RGB").save(os.path.join(d, "icon.png"))
    with open(os.path.join(d, "Contents.json"), "w") as f:
        f.write('{\n  "images" : [\n    {\n      "filename" : "icon.png",\n      "idiom" : "universal",\n'
                '      "platform" : "ios",\n      "size" : "1024x1024"\n    }\n  ],\n'
                '  "info" : {\n    "author" : "xcode",\n    "version" : 1\n  }\n}\n')


def hacker():
    shield = [P(54, 27), P(79, 36), P(79, 54)]
    shield += [P(*q) for q in cubic((79, 54), (79, 69), (68, 79), (54, 84))]
    shield += [P(*q) for q in cubic((54, 84), (40, 79), (29, 69), (29, 54))]
    shield += [P(29, 36)]
    chevron = [P(42, 48), P(50, 55), P(42, 62)]
    cursor = [P(54, 63), P(66, 63)]
    unit = N / CROP
    green = (0, 255, 65)

    img = Image.new("RGB", (N, N), (0, 0, 0))
    # CRT chiziqlari
    d = ImageDraw.Draw(img)
    for y in range(0, N, 6 * SS):
        d.line([(0, y), (N, y)], fill=(0, 22, 8), width=SS)

    def strokes(draw, w):
        draw.line(shield + [shield[0]], fill=green, width=int(w * 3 * unit / 3.2), joint="curve")
        draw.line(chevron, fill=green, width=int(w * 4 * unit / 3.2), joint="curve")
        draw.line(cursor, fill=green, width=int(w * 4 * unit / 3.2))

    # Neon yorug'lik: chiziqlarni niqob sifatida chizib, xiralashtiramiz va yashil rang bilan quyamiz
    mask = Image.new("L", (N, N), 0)
    md = ImageDraw.Draw(mask)
    md.polygon(shield, fill=60)
    md.line(shield + [shield[0]], fill=255, width=int(2.4 * 3 * unit / 3.2), joint="curve")
    md.line(chevron, fill=255, width=int(2.4 * 4 * unit / 3.2), joint="curve")
    md.line(cursor, fill=255, width=int(2.4 * 4 * unit / 3.2))
    mask = mask.filter(ImageFilter.GaussianBlur(26 * SS))
    img.paste(green, (0, 0), mask.point(lambda v: int(v * 0.55)))

    d = ImageDraw.Draw(img)
    d.polygon(shield, fill=(2, 26, 8))
    strokes(d, 1.0)
    # Chiziq uchlarini yumaloqlash
    for (x, y) in chevron[::2] + cursor:
        r = 2 * unit / 3.2
        d.ellipse([x - r, y - r, x + r, y + r], fill=green)
    save(img, "AppIcon-Hacker.appiconset")


def family():
    def F(x, y):  # 24 -> 108 (Android'dagi group: scale 2.6, translate 22.8, 23)
        return P(22.8 + 2.6 * x, 23 + 2.6 * y)

    shield = [F(12, 1), F(3, 5), F(3, 11)]
    shield += [F(*q) for q in cubic((3, 11), (3, 16.55), (6.84, 21.74), (12, 23))]
    shield += [F(*q) for q in cubic((12, 23), (17.16, 21.74), (21, 16.55), (21, 11))]
    shield += [F(21, 5)]
    check = [F(10, 17), F(6, 13), F(7.41, 11.59), F(10, 14.17), F(16.59, 7.58), F(18, 9)]

    # Yuqoridan pastga yumshoq ko'k gradient
    top, bottom = (58, 134, 255), (24, 92, 214)
    grad = Image.new("RGB", (1, N))
    for y in range(N):
        t = y / (N - 1)
        grad.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    img = grad.resize((N, N))

    shadow = Image.new("L", (N, N), 0)
    ImageDraw.Draw(shadow).polygon([(x, y + 18 * SS) for x, y in shield], fill=90)
    shadow = shadow.filter(ImageFilter.GaussianBlur(24 * SS))
    img.paste((10, 50, 140), (0, 0), shadow)

    d = ImageDraw.Draw(img)
    d.polygon(shield, fill=(255, 255, 255))
    d.polygon(check, fill=(31, 111, 235))
    save(img, "AppIcon-Family.appiconset")


hacker()
family()
print("ok")
