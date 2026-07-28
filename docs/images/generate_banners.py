#!/usr/bin/env python3
"""
Generates the article banner images.

One banner per publishing platform, each at that platform's native cover ratio so nothing is cropped
badly by the CMS, and each with a DIFFERENT layout — a single recoloured template reads as five
copies of the same image when the articles are seen together.

    dzone     split panel        two engines face off across a divider
    medium    editorial          centred, quiet, magazine cover
    devto     terminal           a shell session showing the real curl and its output
    linkedin  data card          left-aligned headline with a stat block
    substack  typographic        minimal, rules and whitespace, newsletter masthead

The palette is shared across all five (from src/main/resources/static/css/styles.css) so they stay
recognisably one project: slate background, Flyway orange, Liquibase teal.

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
BG_INSET = "#131c2e"
BORDER = "#2a3650"
TEXT = "#e6ecf7"
MUTED = "#94a3b8"
FLYWAY = "#f2784b"
LIQUIBASE = "#35c4d0"
OK = "#4ade80"
VIOLET = "#a78bfa"

SANS = "Helvetica Neue, Helvetica, Arial, sans-serif"
MONO = "Menlo, Consolas, monospace"
SERIF = "Georgia, Times New Roman, serif"

OUT_DIR = Path(__file__).parent


@dataclass(frozen=True)
class Banner:
    slug: str
    width: int
    height: int
    style: str


BANNERS = [
    Banner("dzone", 1200, 628, "split"),
    Banner("medium", 1400, 700, "editorial"),
    Banner("devto", 1000, 420, "terminal"),
    Banner("linkedin", 1280, 720, "card"),
    Banner("substack", 1200, 600, "typographic"),
]

DEFS = f"""  <defs>
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
      <stop offset="0%" stop-color="{FLYWAY}" stop-opacity="0.24"/>
      <stop offset="100%" stop-color="{FLYWAY}" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="glowR" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0%" stop-color="{LIQUIBASE}" stop-opacity="0.24"/>
      <stop offset="100%" stop-color="{LIQUIBASE}" stop-opacity="0"/>
    </radialGradient>
  </defs>
"""


def frame(w: int, h: int, body: str, bg_extra: str = "") -> str:
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}">\n{DEFS}'
            f'  <rect width="{w}" height="{h}" fill="url(#bg)"/>\n{bg_extra}{body}</svg>\n')


# --------------------------------------------------------------------------- dzone: split panel

def style_split(w: int, h: int) -> str:
    """Two engines facing off across a central divider."""
    s = min(w / 1200, h / 628)
    p = lambda v: round(v * s, 2)
    mid = w / 2

    def side(x0: float, x1: float, name: str, colour: str, model: str, count: str, anchor: str) -> str:
        cx = (x0 + x1) / 2
        return f"""
  <text x="{cx}" y="{h * 0.34}" text-anchor="{anchor}" font-family="{SANS}" font-size="{p(76)}"
        font-weight="700" fill="{colour}" letter-spacing="{p(-1.5)}">{name}</text>
  <text x="{cx}" y="{h * 0.455}" text-anchor="{anchor}" font-family="{SANS}" font-size="{p(21)}"
        fill="{MUTED}">{model}</text>
  <text x="{cx}" y="{h * 0.65}" text-anchor="{anchor}" font-family="{MONO}" font-size="{p(58)}"
        font-weight="700" fill="{colour}">{count}</text>
  <text x="{cx}" y="{h * 0.725}" text-anchor="{anchor}" font-family="{SANS}" font-size="{p(19)}"
        fill="{MUTED}" letter-spacing="{p(2)}">APPLIED</text>"""

    bg = (f'  <rect x="0" y="0" width="{mid}" height="{h}" fill="{FLYWAY}" fill-opacity="0.07"/>\n'
          f'  <rect x="{mid}" y="0" width="{mid}" height="{h}" fill="{LIQUIBASE}" fill-opacity="0.07"/>\n'
          f'  <ellipse cx="{p(180)}" cy="{p(90)}" rx="{p(520)}" ry="{p(360)}" fill="url(#glowL)"/>\n'
          f'  <ellipse cx="{w - p(180)}" cy="{h - p(90)}" rx="{p(520)}" ry="{p(360)}" fill="url(#glowR)"/>\n')

    body = f"""
  <rect x="0" y="0" width="{w}" height="{p(6)}" fill="url(#rule)"/>
  <text x="{mid}" y="{h * 0.145}" text-anchor="middle" font-family="{SANS}" font-size="{p(20)}"
        fill="{MUTED}" letter-spacing="{p(6)}" font-weight="600">SAME SCHEMA, BUILT TWICE</text>
{side(0, mid, "Flyway", FLYWAY, "Versioned SQL scripts", "6", "middle")}
{side(mid, w, "Liquibase", LIQUIBASE, "Changesets in a changelog", "7", "middle")}
  <line x1="{mid}" y1="{h * 0.20}" x2="{mid}" y2="{h * 0.78}" stroke="{BORDER}"
        stroke-width="{p(2)}"/>
  <circle cx="{mid}" cy="{h * 0.49}" r="{p(34)}" fill="{BG}" stroke="{BORDER}" stroke-width="{p(2)}"/>
  <text x="{mid}" y="{h * 0.49 + p(9)}" text-anchor="middle" font-family="{SERIF}"
        font-size="{p(26)}" fill="{MUTED}" font-style="italic">vs</text>
  <text x="{mid}" y="{h * 0.90}" text-anchor="middle" font-family="{MONO}" font-size="{p(24)}"
        fill="{OK}">"schemasEquivalent": true</text>
  <text x="{mid}" y="{h * 0.955}" text-anchor="middle" font-family="{SANS}" font-size="{p(17)}"
        fill="{MUTED}">zero structural differences</text>
"""
    return frame(w, h, body, bg)


# ----------------------------------------------------------------------- medium: editorial cover

def style_editorial(w: int, h: int) -> str:
    """Quiet, centred, magazine-cover restraint. Type does the work."""
    s = min(w / 1400, h / 700)
    p = lambda v: round(v * s, 2)
    cx = w / 2

    bg = (f'  <ellipse cx="{p(170)}" cy="{p(130)}" rx="{p(620)}" ry="{p(470)}" fill="url(#glowL)"/>\n'
          f'  <ellipse cx="{w - p(170)}" cy="{h - p(120)}" rx="{p(620)}" ry="{p(470)}" fill="url(#glowR)"/>\n')

    body = f"""
  <rect x="{p(56)}" y="{p(56)}" width="{w - p(112)}" height="{h - p(112)}" fill="none"
        stroke="{BORDER}" stroke-width="{p(1.5)}"/>
  <text x="{cx}" y="{h * 0.235}" text-anchor="middle" font-family="{SERIF}" font-size="{p(23)}"
        fill="{MUTED}" font-style="italic">an experiment, not an opinion</text>

  <text x="{cx}" y="{h * 0.44}" text-anchor="middle" font-family="{SERIF}" font-size="{p(96)}"
        font-weight="700" letter-spacing="{p(-2)}">
    <tspan fill="{FLYWAY}">Flyway</tspan><tspan fill="{MUTED}" dx="{p(24)}" font-size="{p(48)}"
      font-style="italic" font-weight="400">vs</tspan><tspan fill="{LIQUIBASE}"
      dx="{p(24)}">Liquibase</tspan>
  </text>

  <line x1="{cx - p(60)}" y1="{h * 0.525}" x2="{cx + p(60)}" y2="{h * 0.525}" stroke="{MUTED}"
        stroke-width="{p(1.5)}"/>

  <text x="{cx}" y="{h * 0.625}" text-anchor="middle" font-family="{SERIF}" font-size="{p(30)}"
        fill="{TEXT}">I built the same schema twice,</text>
  <text x="{cx}" y="{h * 0.685}" text-anchor="middle" font-family="{SERIF}" font-size="{p(30)}"
        fill="{TEXT}">then diffed the result.</text>

  <text x="{cx}" y="{h * 0.845}" text-anchor="middle" font-family="{MONO}" font-size="{p(21)}"
        fill="{OK}">"schemasEquivalent": true</text>
"""
    return frame(w, h, body, bg)


# ------------------------------------------------------------------------- devto: terminal window

def style_terminal(w: int, h: int) -> str:
    """A shell session. Dev.to readers are there to run the thing."""
    s = min(w / 1000, h / 420)
    p = lambda v: round(v * s, 2)

    mx, my = p(46), p(38)
    tw, th = w - 2 * mx, h - 2 * my
    bar = p(38)
    line = p(30)
    left = mx + p(26)
    y0 = my + bar + p(40)

    def row(i: int, spans: str) -> str:
        # xml:space="preserve" is required: SVG otherwise collapses the whitespace at tspan
        # boundaries, rendering "applied " + "6 migrations" as "applied6 migrations" and eating
        # the leading indent on the JSON lines.
        return (f'  <text x="{left}" y="{y0 + i * line}" font-family="{MONO}" '
                f'font-size="{p(19)}" xml:space="preserve">{spans}</text>')

    rows = "\n".join([
        row(0, f'<tspan fill="{OK}">$</tspan> <tspan fill="{TEXT}">./start.sh</tspan>'),
        row(1, f'<tspan fill="{MUTED}">==&gt; Flyway applied </tspan>'
               f'<tspan fill="{FLYWAY}">6 migrations</tspan>'),
        row(2, f'<tspan fill="{MUTED}">==&gt; Liquibase applied </tspan>'
               f'<tspan fill="{LIQUIBASE}">7 changesets</tspan>'),
        row(3, f'<tspan fill="{OK}">$</tspan> <tspan fill="{TEXT}">curl -s localhost:8080/api/v1/comparison</tspan>'),
        row(4, f'<tspan fill="{MUTED}">  "</tspan><tspan fill="{VIOLET}">schemasEquivalent</tspan>'
               f'<tspan fill="{MUTED}">": </tspan><tspan fill="{OK}">true</tspan><tspan fill="{MUTED}">,</tspan>'),
        row(5, f'<tspan fill="{MUTED}">  "</tspan><tspan fill="{VIOLET}">schemaDifferences</tspan>'
               f'<tspan fill="{MUTED}">": []</tspan>'),
    ])

    dots = "\n".join(
        f'  <circle cx="{mx + p(26) + i * p(24)}" cy="{my + bar / 2}" r="{p(6.5)}" fill="{c}"/>'
        for i, c in enumerate(("#ff5f57", "#febc2e", "#28c840")))

    body = f"""
  <rect x="{mx}" y="{my}" width="{tw}" height="{th}" rx="{p(12)}" fill="{BG_INSET}"
        stroke="{BORDER}" stroke-width="{p(1.5)}"/>
  <path d="M {mx} {my + p(12)} a {p(12)} {p(12)} 0 0 1 {p(12)} {-p(12)} h {tw - p(24)}
           a {p(12)} {p(12)} 0 0 1 {p(12)} {p(12)} v {bar - p(12)} h {-tw} z" fill="{BG_RAISED}"/>
  <line x1="{mx}" y1="{my + bar}" x2="{mx + tw}" y2="{my + bar}" stroke="{BORDER}"
        stroke-width="{p(1.5)}"/>
{dots}
  <text x="{mx + tw / 2}" y="{my + bar * 0.68}" text-anchor="middle" font-family="{MONO}"
        font-size="{p(16)}" fill="{MUTED}">flyway-vs-liquibase-db-migrations</text>
{rows}
  <text x="{mx + tw - p(26)}" y="{my + th - p(28)}" text-anchor="end" font-family="{SANS}"
        font-size="{p(26)}" font-weight="700">
    <tspan fill="{FLYWAY}">Flyway</tspan><tspan fill="{MUTED}" dx="{p(9)}"
      font-weight="400">vs</tspan><tspan fill="{LIQUIBASE}" dx="{p(9)}">Liquibase</tspan>
  </text>
"""
    return frame(w, h, body)


# ---------------------------------------------------------------------------- linkedin: data card

def style_card(w: int, h: int) -> str:
    """Left-aligned headline, stat block on the right. Reads at feed size."""
    s = min(w / 1280, h / 720)
    p = lambda v: round(v * s, 2)
    left = p(84)

    bg = (f'  <ellipse cx="{p(120)}" cy="{h - p(80)}" rx="{p(560)}" ry="{p(430)}" fill="url(#glowL)"/>\n'
          f'  <ellipse cx="{w - p(120)}" cy="{p(80)}" rx="{p(560)}" ry="{p(430)}" fill="url(#glowR)"/>\n')

    card_x, card_w = w - p(430), p(346)
    card_y, card_h = h * 0.30, p(300)

    def stat(i: int, label: str, value: str, colour: str) -> str:
        y = card_y + p(74) + i * p(96)
        return f"""
  <text x="{card_x + p(28)}" y="{y}" font-family="{SANS}" font-size="{p(19)}"
        fill="{MUTED}">{label}</text>
  <text x="{card_x + card_w - p(28)}" y="{y}" text-anchor="end" font-family="{MONO}"
        font-size="{p(40)}" font-weight="700" fill="{colour}">{value}</text>
  <line x1="{card_x + p(28)}" y1="{y + p(26)}" x2="{card_x + card_w - p(28)}" y2="{y + p(26)}"
        stroke="{BORDER}" stroke-width="{p(1)}"/>"""

    body = f"""
  <rect x="0" y="0" width="{p(10)}" height="{h}" fill="url(#rule)"/>

  <text x="{left}" y="{h * 0.22}" font-family="{SANS}" font-size="{p(19)}" fill="{MUTED}"
        letter-spacing="{p(5)}" font-weight="600">MIGRATION TOOLING</text>

  <text x="{left}" y="{h * 0.365}" font-family="{SANS}" font-size="{p(70)}" font-weight="700"
        letter-spacing="{p(-1.5)}" fill="{FLYWAY}">Flyway</text>
  <text x="{left}" y="{h * 0.475}" font-family="{SANS}" font-size="{p(70)}" font-weight="700"
        letter-spacing="{p(-1.5)}">
    <tspan fill="{MUTED}" font-size="{p(40)}" font-weight="400">vs</tspan><tspan
      fill="{LIQUIBASE}" dx="{p(16)}">Liquibase</tspan>
  </text>

  <!-- A rect, not a line: a gradient stroke on a horizontal line has a zero-height object
       bounding box, so url(#rule) cannot resolve and the rule renders invisible. -->
  <rect x="{left}" y="{h * 0.545 - p(2)}" width="{p(120)}" height="{p(5)}" rx="{p(2.5)}"
        fill="url(#rule)"/>

  <text x="{left}" y="{h * 0.645}" font-family="{SANS}" font-size="{p(26)}" fill="{TEXT}">Same schema. Two engines.</text>
  <text x="{left}" y="{h * 0.705}" font-family="{SANS}" font-size="{p(26)}" fill="{TEXT}">What actually differs.</text>

  <rect x="{card_x}" y="{card_y}" width="{card_w}" height="{card_h}" rx="{p(14)}"
        fill="{BG_RAISED}" fill-opacity="0.85" stroke="{BORDER}" stroke-width="{p(1.5)}"/>
{stat(0, "Flyway migrations", "6", FLYWAY)}
{stat(1, "Liquibase changesets", "7", LIQUIBASE)}
  <text x="{card_x + p(28)}" y="{card_y + card_h - p(46)}" font-family="{SANS}" font-size="{p(19)}"
        fill="{MUTED}">Schema differences</text>
  <text x="{card_x + card_w - p(28)}" y="{card_y + card_h - p(46)}" text-anchor="end"
        font-family="{MONO}" font-size="{p(40)}" font-weight="700" fill="{OK}">0</text>
"""
    return frame(w, h, body, bg)


# ------------------------------------------------------------------- substack: typographic minimal

def style_typographic(w: int, h: int) -> str:
    """Newsletter masthead. Rules, whitespace and one strong line."""
    s = min(w / 1200, h / 600)
    p = lambda v: round(v * s, 2)
    cx = w / 2

    body = f"""
  <line x1="{p(90)}" y1="{p(96)}" x2="{w - p(90)}" y2="{p(96)}" stroke="{BORDER}"
        stroke-width="{p(1.5)}"/>
  <line x1="{p(90)}" y1="{h - p(96)}" x2="{w - p(90)}" y2="{h - p(96)}" stroke="{BORDER}"
        stroke-width="{p(1.5)}"/>

  <text x="{p(90)}" y="{p(70)}" font-family="{SANS}" font-size="{p(18)}" fill="{MUTED}"
        letter-spacing="{p(4)}" font-weight="600">THE NEWSLETTER</text>
  <text x="{w - p(90)}" y="{p(70)}" text-anchor="end" font-family="{SANS}" font-size="{p(18)}"
        fill="{MUTED}" letter-spacing="{p(2)}">wallaceespindola</text>

  <text x="{cx}" y="{h * 0.40}" text-anchor="middle" font-family="{SERIF}" font-size="{p(88)}"
        font-weight="400" letter-spacing="{p(-1)}">
    <tspan fill="{FLYWAY}">Flyway</tspan><tspan fill="{MUTED}" dx="{p(22)}" font-size="{p(44)}"
      font-style="italic">vs</tspan><tspan fill="{LIQUIBASE}" dx="{p(22)}">Liquibase</tspan>
  </text>

  <text x="{cx}" y="{h * 0.545}" text-anchor="middle" font-family="{SERIF}" font-size="{p(28)}"
        fill="{TEXT}" font-style="italic">I made them prove it, in the same application.</text>

  <g font-family="{MONO}" font-size="{p(20)}">
    <text x="{cx - p(230)}" y="{h * 0.705}" text-anchor="middle" fill="{FLYWAY}">6 migrations</text>
    <text x="{cx}" y="{h * 0.705}" text-anchor="middle" fill="{MUTED}">·</text>
    <text x="{cx + p(230)}" y="{h * 0.705}" text-anchor="middle" fill="{LIQUIBASE}">7 changesets</text>
  </g>
  <!-- Kept clear of the bottom rule at h - 96; sitting on it read as a collision. -->
  <text x="{cx}" y="{h * 0.785}" text-anchor="middle" font-family="{MONO}" font-size="{p(22)}"
        fill="{OK}">0 schema differences</text>
"""
    return frame(w, h, body)


STYLES = {
    "split": style_split,
    "editorial": style_editorial,
    "terminal": style_terminal,
    "card": style_card,
    "typographic": style_typographic,
}


def main() -> int:
    if shutil.which("rsvg-convert") is None:
        print("ERROR: rsvg-convert not found. Install librsvg (macOS: brew install librsvg).",
              file=sys.stderr)
        return 1

    for b in BANNERS:
        svg_path = OUT_DIR / f"banner-{b.slug}.svg"
        png_path = OUT_DIR / f"banner-{b.slug}.png"

        svg_path.write_text(STYLES[b.style](b.width, b.height), encoding="utf-8")
        subprocess.run(["rsvg-convert", "-w", str(b.width), "-h", str(b.height),
                        "-o", str(png_path), str(svg_path)], check=True)

        print(f"  {png_path.name:26} {b.width}x{b.height:<5} {b.style:12} "
              f"{png_path.stat().st_size / 1024:6.1f} KB")

    print(f"\n{len(BANNERS)} banners written to {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
