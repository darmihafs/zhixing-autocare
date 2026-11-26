"""Build the unified light product image set.

Source photos live in <repo>/vibe_images. They are cropped, lightly
desaturated and re-encoded so product cards share the same bright,
daylight look as the shop / service images. Run from the frontend
directory:

    python scripts/build_product_images.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "vibe_images"
TARGET = Path(__file__).resolve().parents[1] / "public" / "images" / "product"

JOBS = {
    "product-oil-filter_1794841642809_d041c575.png": ("bosch-oil-filter.jpg", (900, 900)),
    "product-car-shampoo_1794841660439_907b4e2b.png": ("turtle-wax.jpg", (900, 900)),
    "product-engine-oil_1794841680777_1e35ba77.png": ("mobil-5w30.jpg", (900, 900)),
    "product-wiper-blades_1794841698719_6cb7cb4c.png": ("bosch-wiper.jpg", (900, 900)),
    "product-cabin-filter_1794841718476_cbaa14a7.png": ("3m-cabin-filter.jpg", (900, 900)),
    "product-tire-inflator_1794841734770_22edb748.png": ("baseus-pump.jpg", (900, 900)),
    "product-tire_1794841750338_ea22b9d1.png": ("michelin-20555r16.jpg", (900, 900)),
    "product-dashcam_1794841770848_e4a51405.png": ("philips-dashcam.jpg", (900, 900)),
}

PLACEHOLDER_SIZE = 900


def center_crop(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    target_ratio = size[0] / size[1]
    width, height = image.size
    ratio = width / height
    if ratio > target_ratio:
        new_width = int(height * target_ratio)
        left = (width - new_width) // 2
        image = image.crop((left, 0, left + new_width, height))
    elif ratio < target_ratio:
        new_height = int(width / target_ratio)
        top = (height - new_height) // 2
        image = image.crop((0, top, width, top + new_height))
    return image.resize(size, Image.LANCZOS)


def grade(image: Image.Image) -> Image.Image:
    image = ImageEnhance.Color(image).enhance(0.9)
    image = ImageEnhance.Contrast(image).enhance(1.03)
    image = ImageEnhance.Brightness(image).enhance(1.015)
    return image.filter(ImageFilter.UnsharpMask(radius=1.2, percent=55, threshold=3))


def make_placeholder() -> Image.Image:
    size = PLACEHOLDER_SIZE
    image = Image.new("RGB", (size, size), "#f7f8fa")
    draw = ImageDraw.Draw(image)

    # Soft warm floor strip, matching the light product photography.
    for row in range(size // 3, size):
        ratio = (row - size // 3) / (size - size // 3)
        color = (
            int(247 - 12 * ratio),
            int(243 - 12 * ratio),
            int(237 - 12 * ratio),
        )
        draw.line([(0, row), (size, row)], fill=color)

    # Minimal line-art product frame in a neutral gray.
    left, top, right, bottom = 330, 300, 570, 470
    line = "#c9cdd4"
    draw.rounded_rectangle([left, top, right, bottom], radius=10, outline=line, width=4)
    draw.line([(left + 26, bottom - 40), (left + 96, 390), (left + 152, bottom - 70),
               (left + 196, 406), (right - 26, bottom - 40)], fill=line, width=4, joint="curve")
    draw.ellipse([right - 96, top + 28, right - 72, top + 52], outline=line, width=4)

    # Neutral caption without brand marks; skip it when no CJK font is available.
    from PIL import ImageFont

    font = None
    for candidate in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf"):
        try:
            font = ImageFont.truetype(candidate, 24)
            break
        except OSError:
            continue
    caption = "暂无商品图片"
    if font:
        left_edge, top_edge, right_edge, bottom_edge = draw.textbbox((0, 0), caption, font=font)
        draw.text(
            ((size - (right_edge - left_edge)) / 2 - left_edge, bottom + 44),
            caption,
            fill="#a9aeb8",
            font=font,
        )
    return image


def main() -> None:
    TARGET.mkdir(parents=True, exist_ok=True)
    for source_name, (output_name, size) in JOBS.items():
        source = SOURCE / source_name
        if not source.exists():
            raise SystemExit(f"missing source image: {source}")
        image = Image.open(source).convert("RGB")
        image = grade(center_crop(image, size))
        image.save(TARGET / output_name, "JPEG", quality=86, optimize=True, progressive=True)
        print(f"{output_name}: {size[0]}x{size[1]}")

    make_placeholder().save(
        TARGET / "placeholder.jpg", "JPEG", quality=86, optimize=True, progressive=True
    )
    print(f"placeholder.jpg: {PLACEHOLDER_SIZE}x{PLACEHOLDER_SIZE}")


if __name__ == "__main__":
    main()
