#!/usr/bin/env python3
"""
Generates the article banner images.

One banner per publishing platform, each at that platform's native cover ratio so nothing is
cropped badly by the CMS. The palette is lifted directly from the dashboard
(src/main/resources/static/css/styles.css) so the articles and the running app look like the same
project: slate background, Flyway orange, Liquibase teal.

Requires: rsvg-convert (librsvg). On macOS: brew install librsvg

Usage:
    python3 docs/images/generate_banners.py

Author: Wallace Espindola
"""

from __future__ import annotations

import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

# Palette, matching src/main/resources/static/css/styles.css
BG = "#0f172a"
BG_RAISED = "#1a2337"
BORDER = "#2a3650"
TEXT = "#e6ecf7"
MUTED = "#94a3b8"
FLYWAY = "#f2784b"
LIQUIBASE = "#35c4d0"
OK = "#4ade80"

FONT = "Helvetica Neue, Helvetica, Arial, sans-serif"
MONO = "Menlo, Consolas, monospace"

OUT_DIR = Path(__file__).parent


@dataclass(frozen=True)
class Banner:
    """One platform's cover image."""

    slug: str
    width: int
    height: int
    kicker: str
    note: str


# Native cover ratios per platform, so each CMS crops as little as possible.
BANNERS = [
    Banner("dzone", 1200, 628, "DZONE", "Two engines. One schema. Zero differences."),
    Banner("medium", 1400, 700, "MEDIUM", "I built the same schema twice and diffed the result."),
    Banner("devto", 1000, 420, "DEV.TO", "Clone it, run it, check the numbers yourself."),
    Banner("linkedin", 1280, 720, "LINKEDIN", "What actually differs, and how to choose."),
    Banner("substack", 1200, 600, "NEWSLETTER", "The comparison, run as an application."),
]


def svg(b: Banner) -> str:
    """Builds the banner as SVG, scaled from a 1200x600 reference design."""
    w, h = b.width, b.height
    s = min(w / 1200, h / 600)          # uniform scale factor
    cx, cy = w / 2, h / 2

    def px(v: float) -> float:
        return round(v * s, 2)

    title = px(104)
    vs_size = px(52)
    kicker_size = px(21)
    note_size = px(27)
    chip_size = px(21)
    mono_size = px(20)

    # Vertical rhythm, all relative to the centre line.
    kicker_y = cy - px(186)
    title_y = cy - px(46)
    rule_y = cy + px(20)
    note_y = cy + px(74)
    chip_y = cy + px(146)
    foot_y = h - px(38)

    chip_w, chip_h, chip_gap = px(196), px(50), px(20)
    chip_r = px(25)
    left_chip_x = cx - chip_w - chip_gap / 2
    right_chip_x = cx + chip_gap / 2

    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="{BG}"/>
      <stop offset="50%" stop-color="{BG_RAISED}"/>
      <stop offset="100%" stop-color="{BG}"/>
    </linearGradient>
    <linearGradient id="rule" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="{FLYWAY}"/>
      <stop offset="100%" stop-color="{LIQUIBASE}"/>
    </linearGradient>
    <radialGradient id="glowL" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0%" stop-color="{FLYWAY}" stop-opacity="0.26"/>
      <stop offset="55%" stop-color="{FLYWAY}" stop-opacity="0.09"/>
      <stop offset="100%" stop-color="{FLYWAY}" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="glowR" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0%" stop-color="{LIQUIBASE}" stop-opacity="0.26"/>
      <stop offset="55%" stop-color="{LIQUIBASE}" stop-opacity="0.09"/>
      <stop offset="100%" stop-color="{LIQUIBASE}" stop-opacity="0"/>
    </radialGradient>
  </defs>

  <rect width="{w}" height="{h}" fill="url(#bg)"/>
  <ellipse cx="{px(150)}" cy="{px(120)}" rx="{px(560)}" ry="{px(430)}" fill="url(#glowL)"/>
  <ellipse cx="{w - px(150)}" cy="{h - px(110)}" rx="{px(560)}" ry="{px(430)}" fill="url(#glowR)"/>

  <!-- accent edges: Flyway left, Liquibase right -->
  <rect x="0" y="0" width="{px(7)}" height="{h}" fill="{FLYWAY}"/>
  <rect x="{w - px(7)}" y="0" width="{px(7)}" height="{h}" fill="{LIQUIBASE}"/>

  <text x="{cx}" y="{kicker_y}" text-anchor="middle" font-family="{FONT}"
        font-size="{kicker_size}" fill="{MUTED}" letter-spacing="{px(5)}"
        font-weight="600">{b.kicker}</text>

  <text x="{cx}" y="{title_y}" text-anchor="middle" font-family="{FONT}"
        font-size="{title}" font-weight="700" letter-spacing="{px(-2)}">
    <tspan fill="{FLYWAY}">Flyway</tspan><tspan fill="{MUTED}" dx="{px(26)}"
      font-size="{vs_size}" font-weight="400">vs</tspan><tspan fill="{LIQUIBASE}"
      dx="{px(26)}">Liquibase</tspan>
  </text>

  <rect x="{cx - px(150)}" y="{rule_y}" width="{px(300)}" height="{px(4)}" rx="{px(2)}"
        fill="url(#rule)"/>

  <text x="{cx}" y="{note_y}" text-anchor="middle" font-family="{FONT}"
        font-size="{note_size}" fill="{TEXT}" opacity="0.92">{b.note}</text>

  <!-- applied-count chips, the two real numbers from the app -->
  <g>
    <rect x="{left_chip_x}" y="{chip_y}" width="{chip_w}" height="{chip_h}" rx="{chip_r}"
          fill="{FLYWAY}" fill-opacity="0.13" stroke="{FLYWAY}" stroke-opacity="0.55"
          stroke-width="{px(1.5)}"/>
    <text x="{left_chip_x + chip_w / 2}" y="{chip_y + chip_h * 0.66}" text-anchor="middle"
          font-family="{MONO}" font-size="{chip_size}" fill="{FLYWAY}">6 migrations</text>

    <rect x="{right_chip_x}" y="{chip_y}" width="{chip_w}" height="{chip_h}" rx="{chip_r}"
          fill="{LIQUIBASE}" fill-opacity="0.13" stroke="{LIQUIBASE}" stroke-opacity="0.55"
          stroke-width="{px(1.5)}"/>
    <text x="{right_chip_x + chip_w / 2}" y="{chip_y + chip_h * 0.66}" text-anchor="middle"
          font-family="{MONO}" font-size="{chip_size}" fill="{LIQUIBASE}">7 changesets</text>
  </g>

  <text x="{cx}" y="{foot_y}" text-anchor="middle" font-family="{MONO}"
        font-size="{mono_size}" fill="{OK}" opacity="0.9">"schemasEquivalent": true</text>
</svg>
"""


def main() -> int:
    if shutil.which("rsvg-convert") is None:
        print("ERROR: rsvg-convert not found. Install librsvg (macOS: brew install librsvg).",
              file=sys.stderr)
        return 1

    for b in BANNERS:
        svg_path = OUT_DIR / f"banner-{b.slug}.svg"
        png_path = OUT_DIR / f"banner-{b.slug}.png"

        svg_path.write_text(svg(b), encoding="utf-8")
        subprocess.run(
            ["rsvg-convert", "-w", str(b.width), "-h", str(b.height),
             "-o", str(png_path), str(svg_path)],
            check=True,
        )

        size_kb = png_path.stat().st_size / 1024
        print(f"  {png_path.name:26} {b.width}x{b.height}  {size_kb:6.1f} KB")

    print(f"\n{len(BANNERS)} banners written to {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
