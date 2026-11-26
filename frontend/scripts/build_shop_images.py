"""Build the unified shop / service image set.

Source photos live in <repo>/vibe_images. They are cropped, lightly
desaturated and re-encoded so every card on the site shares the same
neutral, daylight look. Run from the frontend directory:

    python scripts/build_shop_images.py
"""

from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "vibe_images"
TARGET = Path(__file__).resolve().parents[1] / "public" / "images" / "shop"

JOBS = {
    "shop-1-exterior_1794838871955_43ce60da.png": ("shop-1.jpg", (1200, 900)),
    "shop-2-interior_1794838891948_23cbd6e9.png": ("shop-2.jpg", (1200, 900)),
    "shop-3-wash_1794838913048_fa66a240.png": ("shop-3.jpg", (1200, 900)),
    "service-maintenance_1794838934733_b4107581.png": ("service-maintenance.jpg", (900, 900)),
    "service-wash_1794838952815_06ef4c64.png": ("service-wash.jpg", (900, 900)),
    "service-tire_1794838968919_49da18c3.png": ("service-tire.jpg", (900, 900)),
    "service-inspection_1794838990068_2fba7507.png": ("service-inspection.jpg", (900, 900)),
    "service-ac_1794840678198_19b5608d.png": ("service-ac.jpg", (900, 900)),
    "service-wiper_1794840702695_84516a7b.png": ("service-wiper.jpg", (900, 900)),
    "banner-maintenance_1794839009066_3ae8e7a9.png": ("banner-maintenance.jpg", (1600, 900)),
}


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


if __name__ == "__main__":
    main()
