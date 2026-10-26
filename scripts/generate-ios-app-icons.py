#!/usr/bin/env python3
"""Generate Morning Guard AppIcon PNGs (matches Android launcher #1B4332 + white bell motif)."""
from __future__ import annotations

import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "ios" / "MorningGuard" / "Assets.xcassets" / "AppIcon.appiconset"

BG = (0x1B, 0x43, 0x32, 255)
FG = (255, 255, 255, 255)


def _png_chunk(tag: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(tag + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)


def write_png(path: Path, size: int) -> None:
    """Minimal RGBA PNG writer (no Pillow dependency)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    pixels = bytearray()
    cx, cy = size / 2, size / 2
    r_outer = size * 0.42
    r_bell = size * 0.22
    for y in range(size):
        row = b"\x00"
        for x in range(size):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            dist = (dx * dx + dy * dy) ** 0.5
            # Rounded square icon background
            if dist <= r_outer:
                # Simple bell: dome + stem + clapper hint
                bell_top = cy - size * 0.12
                bell_bottom = cy + size * 0.18
                in_dome = (
                    dy >= bell_top
                    and dy <= bell_bottom
                    and abs(dx) <= size * 0.14 + max(0, (dy - bell_top) * 0.35)
                )
                in_stem = abs(dx) <= size * 0.04 and bell_bottom <= dy <= bell_bottom + size * 0.06
                in_base = (
                    abs(dx) <= size * 0.08
                    and bell_bottom + size * 0.04 <= dy <= bell_bottom + size * 0.09
                )
                if in_dome or in_stem or in_base:
                    row += bytes(FG)
                else:
                    row += bytes(BG)
            else:
                row += bytes((0, 0, 0, 0))
        pixels += row

    raw = zlib.compress(pixels, 9)
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + _png_chunk(b"IHDR", ihdr)
        + _png_chunk(b"IDAT", raw)
        + _png_chunk(b"IEND", b"")
    )
    path.write_bytes(png)


ICON_SLOTS = [
    ("iphone", "20x20", "2x", 40),
    ("iphone", "20x20", "3x", 60),
    ("iphone", "29x29", "2x", 58),
    ("iphone", "29x29", "3x", 87),
    ("iphone", "40x40", "2x", 80),
    ("iphone", "40x40", "3x", 120),
    ("iphone", "60x60", "2x", 120),
    ("iphone", "60x60", "3x", 180),
    ("ipad", "20x20", "1x", 20),
    ("ipad", "20x20", "2x", 40),
    ("ipad", "29x29", "1x", 29),
    ("ipad", "29x29", "2x", 58),
    ("ipad", "40x40", "1x", 40),
    ("ipad", "40x40", "2x", 80),
    ("ipad", "76x76", "1x", 76),
    ("ipad", "76x76", "2x", 152),
    ("ipad", "83.5x83.5", "2x", 167),
    ("ios-marketing", "1024x1024", "1x", 1024),
]


def main() -> None:
    images = []
    for idiom, size_str, scale, px in ICON_SLOTS:
        name = f"icon-{px}.png"
        write_png(OUT / name, px)
        entry = {
            "filename": name,
            "idiom": idiom,
            "scale": scale,
            "size": size_str,
        }
        if idiom == "ios-marketing":
            entry.pop("scale", None)
        images.append(entry)

    contents = {"images": images, "info": {"author": "xcode", "version": 1}}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n")

    assets_root = OUT.parent
    (assets_root / "Contents.json").write_text(
        json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n"
    )
    print(f"Wrote {len(images)} icons under {OUT}")


if __name__ == "__main__":
    main()
