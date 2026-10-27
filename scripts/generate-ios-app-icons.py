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
    bell_top = -size * 0.12
    bell_bottom = size * 0.18
    for y in range(size):
        row = b"\x00"
        for x in range(size):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
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
                row += bytes(FG[:3])
            else:
                row += bytes(BG[:3])
        pixels += row

    raw = zlib.compress(pixels, 9)
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + _png_chunk(b"IHDR", ihdr)
        + _png_chunk(b"IDAT", raw)
        + _png_chunk(b"IEND", b"")
    )
    path.write_bytes(png)


def main() -> None:
    name = "icon-1024.png"
    write_png(OUT / name, 1024)
    images = [
        {
            "filename": name,
            "idiom": "universal",
            "platform": "ios",
            "size": "1024x1024",
        }
    ]
    contents = {"images": images, "info": {"author": "xcode", "version": 1}}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n")

    assets_root = OUT.parent
    (assets_root / "Contents.json").write_text(
        json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n"
    )
    print(f"Wrote App Store icon under {OUT}")


if __name__ == "__main__":
    main()
