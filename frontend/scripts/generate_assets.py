"""生成洪城养车种子商品图：统一深色棚拍风格，输出到 frontend/public/images/product。"""
from pathlib import Path
from math import cos, sin, radians
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).resolve().parents[1] / "public" / "images" / "product"
OUT.mkdir(parents=True, exist_ok=True)
S = 900
SS = 2
W = S * SS

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"
FONT_LATIN = "C:/Windows/Fonts/arialbd.ttf"
FONT_MONO = "C:/Windows/Fonts/consola.ttf"


def font(path, size):
    return ImageFont.truetype(path, size * SS)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vertical_gradient(top, bottom):
    img = Image.new("RGB", (W, W), top)
    d = ImageDraw.Draw(img)
    for y in range(W):
        d.line([(0, y), (W, y)], fill=lerp(top, bottom, y / W))
    return img


def add_glow(img, center, radius, color, alpha=110):
    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = center[0] * SS, center[1] * SS
    r = radius * SS
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(120 * SS // 2))
    img.alpha_composite(layer)


def add_grid(img):
    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    step = 60 * SS
    for x in range(0, W, step):
        d.line([(x, 0), (x, W)], fill=(255, 255, 255, 12), width=SS)
    for y in range(0, W, step):
        d.line([(0, y), (W, y)], fill=(255, 255, 255, 12), width=SS)
    img.alpha_composite(layer)


def shadow(img, box, blur=26, alpha=120):
    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([box[0] * SS, box[1] * SS, box[2] * SS, box[3] * SS], fill=(0, 0, 0, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(blur * SS // 2))
    img.alpha_composite(layer)


def frame_text(img, number, brand, spec, accent, category):
    d = ImageDraw.Draw(img)
    d.text((54 * SS, 46 * SS), number, font=font(FONT_MONO, 42), fill=(255, 255, 255, 26))
    d.text((56 * SS, 112 * SS), "HONGCHENG SELECT", font=font(FONT_MONO, 11), fill=(255, 255, 255, 90))
    d.line([(56 * SS, 138 * SS), (150 * SS, 138 * SS)], fill=accent + (180,), width=2 * SS)

    d.text((56 * SS, 742 * SS), brand, font=font(FONT_LATIN, 30), fill=(255, 255, 255, 232))
    d.text((58 * SS, 792 * SS), spec, font=font(FONT_MONO, 13), fill=(255, 255, 255, 128))
    label = category
    bbox = d.textbbox((0, 0), label, font=font(FONT_REG, 12))
    w = bbox[2] - bbox[0]
    d.rounded_rectangle([W - 56 * SS - w - 30 * SS, 748 * SS, W - 56 * SS, 784 * SS],
                        radius=6 * SS, outline=accent + (120,), width=SS)
    d.text((W - 56 * SS - w - 15 * SS, 757 * SS), label, font=font(FONT_REG, 12), fill=(255, 255, 255, 180))

    d.text((W - 76 * SS, W - 62 * SS), "01", font=font(FONT_MONO, 9), fill=(255, 255, 255, 0))


def vignette(img):
    layer = Image.new("L", (W, W), 0)
    d = ImageDraw.Draw(layer)
    d.ellipse([-W * 0.25, -W * 0.25, W * 1.25, W * 1.25], fill=255)
    layer = layer.filter(ImageFilter.GaussianBlur(160))
    black = Image.new("RGBA", (W, W), (0, 0, 0, 150))
    img.alpha_composite(Image.composite(Image.new("RGBA", (W, W), (0, 0, 0, 0)), black, layer))


def base(top, bottom, accent, glow_at=(660, 230), glow_r=300, glow_alpha=85):
    img = vertical_gradient(top, bottom).convert("RGBA")
    add_glow(img, glow_at, glow_r, accent, glow_alpha)
    add_grid(img)
    return img


def save(img, name):
    vignette(img)
    img.convert("RGB").resize((S, S), Image.LANCZOS).save(OUT / name, quality=92, optimize=True)


def draw_oil():
    accent = (255, 90, 31)
    img = base((24, 32, 46), (10, 14, 22), accent)
    d = ImageDraw.Draw(img)
    shadow(img, (320, 680, 610, 748), 30, 150)

    body = [(360, 268), (540, 268), (566, 330), (566, 684), (540, 716), (360, 716), (334, 684), (334, 330)]
    d.polygon([(x * SS, y * SS) for x, y in body], fill=(206, 212, 220))
    d.rounded_rectangle([356 * SS, 250 * SS, 544 * SS, 330 * SS], radius=26 * SS, fill=(190, 197, 207))
    d.rounded_rectangle([388 * SS, 176 * SS, 512 * SS, 262 * SS], radius=16 * SS, fill=(38, 44, 56))
    d.rounded_rectangle([388 * SS, 196 * SS, 512 * SS, 216 * SS], radius=6 * SS, fill=(82, 92, 108))
    # 侧把手
    d.rounded_rectangle([520 * SS, 310 * SS, 590 * SS, 520 * SS], radius=26 * SS, fill=(18, 24, 34))
    d.rounded_rectangle([536 * SS, 326 * SS, 574 * SS, 504 * SS], radius=18 * SS, fill=(196, 203, 213))

    # 标签
    d.rounded_rectangle([356 * SS, 396 * SS, 544 * SS, 600 * SS], radius=10 * SS, fill=(255, 90, 31))
    d.rectangle([356 * SS, 396 * SS, 544 * SS, 426 * SS], fill=(16, 24, 40))
    d.text((372 * SS, 440 * SS), "1", font=font(FONT_LATIN, 64), fill=(255, 255, 255))
    d.text((374 * SS, 528 * SS), "5W-30", font=font(FONT_MONO, 22), fill=(255, 255, 255))
    d.text((374 * SS, 562 * SS), "FULL SYNTHETIC", font=font(FONT_MONO, 9), fill=(255, 226, 214))
    d.text((372 * SS, 402 * SS), "MOBIL", font=font(FONT_LATIN, 12), fill=(255, 255, 255))

    # 高光
    d.line([(364 * SS, 330 * SS), (364 * SS, 680 * SS)], fill=(255, 255, 255, 120), width=7 * SS)
    d.line([(380 * SS, 340 * SS), (380 * SS, 660 * SS)], fill=(255, 255, 255, 55), width=3 * SS)
    d.rounded_rectangle([384 * SS, 184 * SS, 414 * SS, 252 * SS], radius=7 * SS, fill=(255, 255, 255, 30))
    frame_text(img, "01", "MOBIL 1", "ADVANCED FULL SYNTHETIC / 4L", accent, "机油")
    save(img, "mobil-5w30.jpg")


def draw_filter():
    accent = (56, 189, 248)
    img = base((18, 32, 36), (9, 15, 19), accent, (600, 220), 320, 70)
    d = ImageDraw.Draw(img)
    shadow(img, (330, 676, 590, 734), 26, 150)

    d.rounded_rectangle([352 * SS, 286 * SS, 548 * SS, 682 * SS], radius=34 * SS, fill=(206, 214, 222))
    d.ellipse([352 * SS, 254 * SS, 548 * SS, 318 * SS], fill=(228, 234, 240))
    d.ellipse([380 * SS, 268 * SS, 520 * SS, 304 * SS], fill=(32, 41, 52))
    d.ellipse([396 * SS, 276 * SS, 504 * SS, 296 * SS], fill=(240, 128, 56))
    d.rounded_rectangle([332 * SS, 318 * SS, 568 * SS, 342 * SS], radius=8 * SS, fill=(24, 32, 42))

    for i in range(12):
        x = 374 + i * 14
        d.rounded_rectangle([x * SS, 356 * SS, (x + 4) * SS, 660 * SS], radius=2 * SS, fill=(148, 160, 174))
    d.rounded_rectangle([352 * SS, 430 * SS, 548 * SS, 548 * SS], radius=8 * SS, fill=(18, 28, 38))
    d.text((376 * SS, 446 * SS), "BOSCH", font=font(FONT_LATIN, 22), fill=(255, 255, 255))
    d.text((376 * SS, 490 * SS), "OIL FILTER", font=font(FONT_MONO, 11), fill=(120, 220, 255))
    d.text((376 * SS, 514 * SS), "0986AF0", font=font(FONT_MONO, 9), fill=(150, 162, 176))
    d.rounded_rectangle([352 * SS, 648 * SS, 548 * SS, 682 * SS], radius=12 * SS, fill=(32, 41, 52))
    d.ellipse([398 * SS, 650 * SS, 502 * SS, 680 * SS], fill=(18, 28, 38))
    frame_text(img, "02", "BOSCH", "OIL FILTER / UNIVERSAL FIT", accent, "滤清器")
    save(img, "bosch-oil-filter.jpg")


def draw_tire():
    accent = (251, 191, 36)
    img = base((34, 30, 26), (12, 11, 12), accent, (640, 220), 330, 75)
    d = ImageDraw.Draw(img)
    shadow(img, (250, 690, 670, 756), 32, 160)

    cx, cy, ro, ri = 450, 460, 236, 108
    d.ellipse([(cx - ro) * SS, (cy - ro) * SS, (cx + ro) * SS, (cy + ro) * SS], fill=(27, 28, 31))
    d.ellipse([(cx - ro + 18) * SS, (cy - ro + 18) * SS, (cx + ro - 18) * SS, (cy + ro - 18) * SS],
              outline=(52, 54, 58), width=5 * SS)
    for i in range(44):
        angle = radians(i * 360 / 44)
        x1 = cx + (ro - 20) * cos(angle)
        y1 = cy + (ro - 20) * sin(angle)
        x2 = cx + (ro + 4) * cos(angle)
        y2 = cy + (ro + 4) * sin(angle)
        d.line([(x1 * SS, y1 * SS), (x2 * SS, y2 * SS)], fill=(64, 66, 70), width=5 * SS)
    d.ellipse([(cx - ri - 26) * SS, (cy - ri - 26) * SS, (cx + ri + 26) * SS, (cy + ri + 26) * SS],
              fill=(18, 20, 24))
    d.ellipse([(cx - ri) * SS, (cy - ri) * SS, (cx + ri) * SS, (cy + ri) * SS], fill=(188, 195, 205))
    d.ellipse([(cx - ri + 14) * SS, (cy - ri + 14) * SS, (cx + ri - 14) * SS, (cy + ri - 14) * SS],
              fill=(126, 134, 148))
    d.ellipse([(cx - 34) * SS, (cy - 34) * SS, (cx + 34) * SS, (cy + 34) * SS], fill=(32, 36, 44))

    for i in range(6):
        angle = radians(i * 60 + 15)
        x1 = cx + 36 * cos(angle)
        y1 = cy + 36 * sin(angle)
        x2 = cx + (ri - 12) * cos(angle)
        y2 = cy + (ri - 12) * sin(angle)
        d.line([(x1 * SS, y1 * SS), (x2 * SS, y2 * SS)], fill=(74, 80, 92), width=18 * SS)

    # 环形高光
    for width, alpha in ((9, 45), (22, 20)):
        d.arc([(cx - ro + 8) * SS, (cy - ro + 8) * SS, (cx + ro - 8) * SS, (cy + ro - 8) * SS],
              start=196, end=330, fill=(255, 255, 255, alpha), width=width * SS)
    d.arc([(cx - ro + 4) * SS, (cy - ro + 4) * SS, (cx + ro - 4) * SS, (cy + ro - 4) * SS],
          start=300, end=360, fill=accent + (210,), width=4 * SS)

    d.text((cx - 52 * SS, cy + 22 * SS), "MICHELIN", font=font(FONT_LATIN, 30), fill=(236, 240, 246))
    d.text((cx - 52 * SS, cy + 62 * SS), "PRIMACY 4", font=font(FONT_MONO, 13), fill=accent)
    frame_text(img, "03", "MICHELIN", "205 / 55 R16  91V", accent, "轮胎")
    save(img, "michelin-20555r16.jpg")


def draw_wiper():
    accent = (96, 165, 250)
    img = base((22, 30, 44), (10, 13, 20), accent, (620, 240), 330, 70)
    d = ImageDraw.Draw(img)
    shadow(img, (240, 690, 660, 750), 30, 150)

    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.rounded_rectangle([150 * SS, 470 * SS, 770 * SS, 506 * SS], radius=16 * SS, fill=(24, 27, 33))
    ld.rounded_rectangle([150 * SS, 496 * SS, 770 * SS, 512 * SS], radius=7 * SS, fill=(72, 77, 86))
    ld.rounded_rectangle([210 * SS, 448 * SS, 700 * SS, 476 * SS], radius=9 * SS, fill=(46, 52, 62))
    ld.polygon([(300 * SS, 448 * SS), (520 * SS, 448 * SS), (470 * SS, 400 * SS), (350 * SS, 400 * SS)],
               fill=(68, 76, 88))
    ld.rounded_rectangle([345 * SS, 382 * SS, 475 * SS, 414 * SS], radius=8 * SS, fill=(92, 102, 118))
    ld.rounded_rectangle([420 * SS, 508 * SS, 500 * SS, 552 * SS], radius=8 * SS, fill=(120, 130, 146))
    ld.ellipse([448 * SS, 524 * SS, 472 * SS, 548 * SS], fill=(32, 38, 48))
    ld.line([(170 * SS, 504 * SS), (748 * SS, 504 * SS)], fill=(255, 255, 255, 70), width=2 * SS)
    layer = layer.rotate(18, resample=Image.BICUBIC, center=(460 * SS, 470 * SS))
    img.alpha_composite(layer)

    d = ImageDraw.Draw(img)
    d.text((190 * SS, 330 * SS), "BOSCH", font=font(FONT_LATIN, 24), fill=(255, 255, 255, 210))
    d.text((190 * SS, 368 * SS), "AEROTWIN", font=font(FONT_MONO, 10), fill=(147, 197, 253))
    frame_text(img, "04", "BOSCH", "AEROTWIN / 24 + 19 INCH", accent, "雨刮")
    save(img, "bosch-wiper.jpg")


def draw_wash():
    accent = (45, 212, 191)
    img = base((12, 34, 38), (7, 16, 19), accent, (640, 230), 320, 80)
    d = ImageDraw.Draw(img)
    shadow(img, (320, 690, 610, 748), 30, 150)

    body = [(348, 330), (552, 330), (578, 400), (560, 700), (340, 700), (322, 400), (348, 330)]
    d.polygon([(x * SS, y * SS) for x, y in body], fill=(72, 188, 178, 230))
    d.polygon([(360 * SS, 350 * SS), (380 * SS, 684 * SS), (340 * SS, 684 * SS), (334 * SS, 410 * SS)],
              fill=(255, 255, 255, 42))
    d.rounded_rectangle([378 * SS, 250 * SS, 522 * SS, 336 * SS], radius=14 * SS, fill=(30, 42, 48))
    d.rounded_rectangle([400 * SS, 214 * SS, 500 * SS, 256 * SS], radius=10 * SS, fill=(50, 66, 74))
    d.rounded_rectangle([548 * SS, 380 * SS, 600 * SS, 560 * SS], radius=24 * SS, fill=(30, 42, 48))

    d.rounded_rectangle([352 * SS, 420 * SS, 556 * SS, 590 * SS], radius=9 * SS, fill=(250, 250, 247))
    d.rectangle([352 * SS, 420 * SS, 556 * SS, 452 * SS], fill=(15, 118, 110))
    d.text((368 * SS, 466 * SS), "CAR", font=font(FONT_LATIN, 34), fill=(16, 24, 40))
    d.text((368 * SS, 508 * SS), "WASH", font=font(FONT_LATIN, 34), fill=(16, 24, 40))
    d.text((370 * SS, 556 * SS), "NEUTRAL pH · 2L", font=font(FONT_MONO, 10), fill=(71, 84, 103))
    d.text((370 * SS, 428 * SS), "TURTLE WAX", font=font(FONT_MONO, 8), fill=(255, 255, 255))

    for cx, cy, r in ((300, 300, 13), (640, 390, 9), (600, 290, 5), (292, 450, 7), (642, 540, 6)):
        d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], outline=(190, 255, 244, 90),
                  width=2 * SS)
    frame_text(img, "05", "TURTLE WAX", "NEUTRAL pH CAR SHAMPOO / 2L", accent, "洗车")
    save(img, "turtle-wax.jpg")


def draw_cabin_filter():
    accent = (167, 139, 250)
    img = base((24, 24, 42), (10, 11, 19), accent, (620, 230), 320, 70)
    d = ImageDraw.Draw(img)
    shadow(img, (280, 660, 630, 730), 28, 150)

    d.rounded_rectangle([300 * SS, 288 * SS, 612 * SS, 660 * SS], radius=18 * SS, fill=(28, 30, 46))
    x = 314
    for i in range(14):
        d.rounded_rectangle([x * SS, 308 * SS, (x + 14) * SS, 640 * SS], radius=5 * SS,
                            fill=(218, 222, 232) if i % 2 == 0 else (192, 198, 212))
        x += 20
    d.rounded_rectangle([286 * SS, 274 * SS, 626 * SS, 306 * SS], radius=9 * SS, fill=(52, 56, 82))
    d.rounded_rectangle([286 * SS, 642 * SS, 626 * SS, 674 * SS], radius=9 * SS, fill=(52, 56, 82))
    d.rounded_rectangle([314 * SS, 404 * SS, 598 * SS, 500 * SS], radius=7 * SS, fill=(24, 24, 40))
    d.text((334 * SS, 418 * SS), "3M", font=font(FONT_LATIN, 40), fill=(255, 255, 255))
    d.text((334 * SS, 468 * SS), "CABIN FILTER · ACTIVATED CARBON", font=font(FONT_MONO, 9),
           fill=(196, 181, 253))
    d.arc([440 * SS, 322 * SS, 500 * SS, 382 * SS], start=200, end=340, fill=accent + (220,), width=4 * SS)
    d.polygon([(470 * SS, 316 * SS), (458 * SS, 334 * SS), (482 * SS, 334 * SS)], fill=accent + (220,))
    frame_text(img, "06", "3M", "PM2.5 / ACTIVATED CARBON", accent, "空调滤")
    save(img, "3m-cabin-filter.jpg")


def draw_pump():
    accent = (250, 204, 21)
    img = base((34, 28, 18), (12, 10, 8), accent, (620, 240), 320, 70)
    d = ImageDraw.Draw(img)
    shadow(img, (300, 680, 620, 744), 30, 155)

    d.rounded_rectangle([318 * SS, 380 * SS, 602 * SS, 700 * SS], radius=24 * SS, fill=(38, 40, 44))
    d.rounded_rectangle([348 * SS, 400 * SS, 572 * SS, 460 * SS], radius=8 * SS, fill=(22, 24, 28))
    d.text((362 * SS, 412 * SS), "TIRE INFLATOR", font=font(FONT_MONO, 11), fill=(250, 204, 21))
    d.rounded_rectangle([340 * SS, 476 * SS, 580 * SS, 670 * SS], radius=14 * SS, fill=(26, 28, 32))
    d.ellipse([366 * SS, 496 * SS, 514 * SS, 644 * SS], fill=(232, 236, 242))
    d.ellipse([378 * SS, 508 * SS, 502 * SS, 632 * SS], fill=(20, 22, 26))
    for i in range(12):
        a = radians(i * 30)
        x1 = 440 + 52 * cos(a)
        y1 = 570 + 52 * sin(a)
        x2 = 440 + 62 * cos(a)
        y2 = 570 + 62 * sin(a)
        d.line([(x1 * SS, y1 * SS), (x2 * SS, y2 * SS)], fill=(230, 234, 240), width=2 * SS)
    d.line([(440 * SS, 570 * SS), (474 * SS, 542 * SS)], fill=(250, 204, 21), width=4 * SS)
    d.ellipse([432 * SS, 562 * SS, 448 * SS, 578 * SS], fill=(250, 204, 21))
    d.text((410 * SS, 596 * SS), "35", font=font(FONT_MONO, 12), fill=(140, 150, 165))
    d.rounded_rectangle([528 * SS, 496 * SS, 566 * SS, 580 * SS], radius=8 * SS, fill=(54, 58, 64))
    d.rounded_rectangle([548 * SS, 500 * SS, 558 * SS, 530 * SS], radius=4 * SS, fill=(250, 204, 21))

    d.arc([180 * SS, 420 * SS, 360 * SS, 600 * SS], start=90, end=300, fill=(64, 68, 74), width=17 * SS)
    d.arc([200 * SS, 440 * SS, 340 * SS, 580 * SS], start=95, end=295, fill=(94, 100, 110), width=4 * SS)
    d.ellipse([186 * SS, 600 * SS, 232 * SS, 646 * SS], fill=(38, 40, 44))
    frame_text(img, "07", "BASEUS", "DIGITAL TIRE INFLATOR", accent, "车载电子")
    save(img, "baseus-pump.jpg")


def draw_dashcam():
    accent = (248, 113, 113)
    img = base((34, 18, 24), (12, 8, 11), accent, (620, 230), 320, 80)
    d = ImageDraw.Draw(img)
    shadow(img, (280, 650, 620, 716), 28, 155)

    d.rounded_rectangle([292 * SS, 318 * SS, 608 * SS, 600 * SS], radius=34 * SS, fill=(34, 36, 42))
    d.rounded_rectangle([312 * SS, 338 * SS, 464 * SS, 580 * SS], radius=18 * SS, fill=(16, 18, 23))
    d.rounded_rectangle([324 * SS, 350 * SS, 452 * SS, 568 * SS], radius=12 * SS, fill=(28, 36, 52))
    d.ellipse([350 * SS, 398 * SS, 426 * SS, 474 * SS], fill=(10, 12, 16))
    d.ellipse([360 * SS, 408 * SS, 416 * SS, 464 * SS], fill=(46, 78, 120))
    d.ellipse([372 * SS, 420 * SS, 398 * SS, 446 * SS], fill=(120, 170, 220))
    d.text((342 * SS, 498 * SS), "2K", font=font(FONT_LATIN, 26), fill=(255, 255, 255))
    d.text((386 * SS, 510 * SS), "NIGHT VISION", font=font(FONT_MONO, 7), fill=(150, 160, 176))

    d.rounded_rectangle([478 * SS, 356 * SS, 590 * SS, 558 * SS], radius=18 * SS, fill=(24, 26, 32))
    for i, (label, color) in enumerate((("REC", (248, 113, 113)), ("WiFi", (110, 231, 183)))):
        y = 386 + i * 56
        d.ellipse([(498 * SS), (y * SS), (510 * SS), ((y + 12) * SS)], fill=color)
        d.text((520 * SS, y - 2 * SS), label, font=font(FONT_MONO, 10), fill=(220, 224, 230))

    d.rounded_rectangle([398 * SS, 260 * SS, 500 * SS, 320 * SS], radius=12 * SS, fill=(48, 52, 60))
    d.rounded_rectangle([420 * SS, 222 * SS, 478 * SS, 270 * SS], radius=8 * SS, fill=(64, 68, 78))
    d.ellipse([430 * SS, 590 * SS, 470 * SS, 626 * SS], fill=(16, 18, 22))
    d.ellipse([256 * SS, 346 * SS, 282 * SS, 372 * SS], fill=accent)
    frame_text(img, "08", "PHILIPS", "2K DASHCAM / PARKING MONITOR", accent, "行车记录")
    save(img, "philips-dashcam.jpg")


def draw_placeholder():
    accent = (255, 90, 31)
    img = base((30, 32, 38), (14, 15, 18), accent, (620, 230), 280, 55)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([330 * SS, 380 * SS, 570 * SS, 580 * SS], radius=18 * SS, outline=(255, 255, 255, 45),
                        width=2 * SS)
    d.line([(380 * SS, 470 * SS), (520 * SS, 470 * SS)], fill=(255, 255, 255, 100), width=8 * SS)
    d.ellipse([370 * SS, 440 * SS, 402 * SS, 472 * SS], outline=(255, 255, 255, 120), width=6 * SS)
    d.text((384 * SS, 500 * SS), "NO IMAGE", font=font(FONT_MONO, 13), fill=(255, 255, 255, 100))
    d.text((54 * SS, 742 * SS), "HONGCHENG", font=font(FONT_LATIN, 24), fill=(255, 255, 255, 180))
    d.text((56 * SS, 788 * SS), "AUTO CARE SELECT", font=font(FONT_MONO, 11), fill=(255, 255, 255, 90))
    save(img, "placeholder.jpg")


if __name__ == "__main__":
    draw_oil()
    draw_filter()
    draw_tire()
    draw_wiper()
    draw_wash()
    draw_cabin_filter()
    draw_pump()
    draw_dashcam()
    draw_placeholder()
    print("generated:", OUT)
