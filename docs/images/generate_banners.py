#!/usr/bin/env python3
"""
Generates the article banner images.

One banner per publishing platform, each at that platform's native cover ratio so nothing is cropped
badly by the CMS, and each with a DIFFERENT layout — a single recoloured template reads as five
copies of the same image when the articles are seen together.

    dzone     convergence        both real file stacks feeding one schema
    medium    editorial          centred, quiet, magazine cover
    devto     diff               the same migration in both formats, side by side
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
    Banner("dzone", 1200, 628, "convergence"),
    Banner("medium", 1400, 700, "editorial"),
    Banner("devto", 1000, 420, "diff"),
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


# ------------------------------------------------------------------- dzone: convergence blueprint

# The real migration sets, so the banner shows the actual repository rather than a decoration.
FLYWAY_FILES = [
    "V1__create_category_table.sql",
    "V2__create_product_table.sql",
    "V3__seed_reference_data.sql",
    "V4__add_product_audit_table.sql",
    "V5__add_product_active_flag.sql",
    "R__product_catalog_view.sql",
]
LIQUIBASE_FILES = [
    "001-create-category-table.xml",
    "002-create-product-table.yaml",
    "003-seed-reference-data.sql",
    "004-add-product-audit-table.xml",
    "005-add-product-active-flag.xml",
    "006-product-catalog-view.xml",
]
SCHEMA_OBJECTS = ["category", "product", "product_audit", "v_product_catalog"]


def style_convergence(w: int, h: int) -> str:
    """
    Two file stacks converging on one database.

    This is the project's actual thesis drawn out: six SQL scripts on the left, six changelog files
    on the right, different formats and different counts, both feeding a single identical schema in
    the middle. The filenames are the real ones from src/main/resources.
    """
    s = min(w / 1200, h / 628)
    p = lambda v: round(v * s, 2)
    cx = w / 2

    card_w, card_h, gap = p(266), p(38), p(9)
    stack_h = 6 * card_h + 5 * gap
    stack_y = h * 0.235
    mid_y = stack_y + stack_h / 2

    left_x, right_x = p(34), w - p(34) - card_w

    def stack(x: float, files: list[str], colour: str) -> str:
        out = []
        for i, name in enumerate(files):
            y = stack_y + i * (card_h + gap)
            # 005 is the one file carrying two changesets. Its accent bar is split in two to show
            # that without a badge, which would overlap the filename — the card is already only just
            # wide enough for it.
            if name.startswith("005"):
                seg = (card_h - p(5)) / 2
                accent = (
                    f'  <rect x="{x}" y="{y}" width="{p(4)}" height="{seg}" rx="{p(2)}" fill="{colour}"/>\n'
                    f'  <rect x="{x}" y="{y + seg + p(5)}" width="{p(4)}" height="{seg}" rx="{p(2)}" '
                    f'fill="{colour}"/>')
            else:
                accent = f'  <rect x="{x}" y="{y}" width="{p(4)}" height="{card_h}" rx="{p(2)}" fill="{colour}"/>'

            out.append(f"""
  <rect x="{x}" y="{y}" width="{card_w}" height="{card_h}" rx="{p(6)}" fill="{BG_INSET}"
        stroke="{BORDER}" stroke-width="{p(1)}"/>
{accent}
  <text x="{x + p(18)}" y="{y + card_h * 0.66}" font-family="{MONO}" font-size="{p(13)}"
        fill="{TEXT}" opacity="0.92">{name}</text>""")
        return "".join(out)

    # Database cylinder holding the schema both engines produced.
    rx, ry = p(112), p(26)
    top_y = stack_y + p(18)
    bot_y = stack_y + stack_h - p(18)

    objects = "".join(
        f"""
  <text x="{cx}" y="{top_y + p(62) + i * p(34)}" text-anchor="middle" font-family="{MONO}"
        font-size="{p(15)}" fill="{TEXT}" opacity="0.9">{o}</text>"""
        for i, o in enumerate(SCHEMA_OBJECTS))

    def arrow(x0: float, x1: float, colour: str) -> str:
        ctrl = (x0 + x1) / 2
        return (f'  <path d="M {x0} {mid_y} C {ctrl} {mid_y} {ctrl} {mid_y} {x1} {mid_y}" '
                f'fill="none" stroke="{colour}" stroke-width="{p(2)}" stroke-opacity="0.7" '
                f'marker-end="url(#head-{"l" if colour == FLYWAY else "r"})"/>')

    markers = f"""
    <marker id="head-l" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="{p(7)}"
            markerHeight="{p(7)}" orient="auto"><path d="M 0 0 L 10 5 L 0 10 z" fill="{FLYWAY}"/></marker>
    <marker id="head-r" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="{p(7)}"
            markerHeight="{p(7)}" orient="auto"><path d="M 0 0 L 10 5 L 0 10 z" fill="{LIQUIBASE}"/></marker>"""

    bar_y, bar_h = h - p(78), p(46)

    body = f"""
  <defs>{markers}
  </defs>

  <rect x="0" y="0" width="{w}" height="{p(5)}" fill="url(#rule)"/>

  <text x="{cx}" y="{p(52)}" text-anchor="middle" font-family="{SANS}" font-size="{p(38)}"
        font-weight="700" letter-spacing="{p(-0.5)}">
    <tspan fill="{FLYWAY}">Flyway</tspan><tspan fill="{MUTED}" dx="{p(11)}" font-size="{p(24)}"
      font-weight="400">vs</tspan><tspan fill="{LIQUIBASE}" dx="{p(11)}">Liquibase</tspan>
  </text>
  <text x="{cx}" y="{p(80)}" text-anchor="middle" font-family="{SANS}" font-size="{p(17)}"
        fill="{MUTED}">Different files. Different counts. One identical schema.</text>

  <text x="{left_x}" y="{stack_y - p(14)}" font-family="{MONO}" font-size="{p(13)}" fill="{FLYWAY}"
        letter-spacing="{p(1)}">db/migration/ — 6 applied</text>
  <text x="{right_x + card_w}" y="{stack_y - p(14)}" text-anchor="end" font-family="{MONO}"
        font-size="{p(13)}" fill="{LIQUIBASE}" letter-spacing="{p(1)}">db/changelog/ — 7 applied</text>

{stack(left_x, FLYWAY_FILES, FLYWAY)}
{stack(right_x, LIQUIBASE_FILES, LIQUIBASE)}

{arrow(left_x + card_w + p(14), cx - rx - p(16), FLYWAY)}
{arrow(right_x - p(14), cx + rx + p(16), LIQUIBASE)}

  <path d="M {cx - rx} {top_y} A {rx} {ry} 0 0 1 {cx + rx} {top_y}
           L {cx + rx} {bot_y} A {rx} {ry} 0 0 1 {cx - rx} {bot_y} Z"
        fill="{BG_RAISED}" fill-opacity="0.92" stroke="{BORDER}" stroke-width="{p(1.5)}"/>
  <ellipse cx="{cx}" cy="{top_y}" rx="{rx}" ry="{ry}" fill="{BG_INSET}" stroke="{BORDER}"
           stroke-width="{p(1.5)}"/>
  <text x="{cx}" y="{top_y + p(6)}" text-anchor="middle" font-family="{SANS}" font-size="{p(12)}"
        fill="{MUTED}" letter-spacing="{p(2)}">SCHEMA</text>
{objects}
  <text x="{cx}" y="{bot_y - p(16)}" text-anchor="middle" font-family="{SANS}" font-size="{p(12)}"
        fill="{MUTED}">3 tables · 1 view · 27 columns</text>

  <!-- Fills the band under the stacks and answers the question the two counts provoke. -->
  <text x="{cx}" y="{stack_y + stack_h + p(40)}" text-anchor="middle" font-family="{SANS}"
        font-size="{p(16)}" fill="{MUTED}">Six files each — but <tspan fill="{LIQUIBASE}"
      font-family="{MONO}" font-size="{p(14)}">005-add-product-active-flag.xml</tspan> holds two
    changesets,</text>
  <text x="{cx}" y="{stack_y + stack_h + p(64)}" text-anchor="middle" font-family="{SANS}"
        font-size="{p(16)}" fill="{MUTED}">splitting the column add from the backfill. That is the
    whole of 6 vs 7.</text>

  <rect x="{p(34)}" y="{bar_y}" width="{w - p(68)}" height="{bar_h}" rx="{p(10)}"
        fill="{OK}" fill-opacity="0.08" stroke="{OK}" stroke-opacity="0.45" stroke-width="{p(1.2)}"/>
  <path d="M {p(74)} {bar_y + bar_h / 2} l {p(9)} {p(10)} l {p(18)} {-p(20)}" fill="none"
        stroke="{OK}" stroke-width="{p(3)}" stroke-linecap="round" stroke-linejoin="round"/>
  <text x="{p(118)}" y="{bar_y + bar_h * 0.64}" font-family="{MONO}" font-size="{p(19)}"
        fill="{OK}">"schemasEquivalent": true</text>
  <text x="{w - p(58)}" y="{bar_y + bar_h * 0.64}" text-anchor="end" font-family="{SANS}"
        font-size="{p(16)}" fill="{MUTED}">verified at runtime, not asserted in prose</text>
"""
    return frame(w, h, body)


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


def style_diff(w: int, h: int) -> str:
    """
    The same migration in both formats, side by side.

    Dev.to readers want the code, so the banner IS the code: V4__add_product_audit_table.sql next to
    004-add-product-audit-table.xml, which is the sharpest single comparison in the repository. The
    Liquibase pane carries a preConditions guard and a <rollback> block, both highlighted; the Flyway
    pane has neither, and says so where they would go.
    """
    s = min(w / 1000, h / 420)
    p = lambda v: round(v * s, 2)

    m, gap = p(20), p(16)
    pane_w = (w - 2 * m - gap) / 2
    pane_y, pane_h = p(66), p(292)
    bar = p(28)
    line_h = p(18.5)
    code_x_pad = p(14)
    code_y0 = pane_y + bar + p(24)

    K, TAG, ATTR, STR, CMT = "#f2784b", "#35c4d0", VIOLET, "#9ae6b4", "#64748b"

    # (segments, highlight) — segments are (text, colour) pairs. Real code, trimmed to fit.
    flyway_code = [
        ([("CREATE TABLE", K), (" product_audit (", TEXT)], None),
        ([("  id", TEXT), ("           BIGINT", ATTR), (" AUTO_INCREMENT PK,", TEXT)], None),
        ([("  product_id", TEXT), ("   BIGINT", ATTR), ("  NOT NULL,", TEXT)], None),
        ([("  audit_action", TEXT), (" VARCHAR(20)", ATTR), (" NOT NULL,", TEXT)], None),
        ([("  changed_by", TEXT), ("   VARCHAR(100)", ATTR), (" DEFAULT", K), (" 'system',", STR)], None),
        ([("  changed_at", TEXT), ("   TIMESTAMP", ATTR), ("  NOT NULL", TEXT)], None),
        ([(");", TEXT)], None),
        ([("", TEXT)], None),
        ([("-- No precondition. No guard.", CMT)], "miss"),
        ([("", TEXT)], None),
        ([("-- No rollback. Reverting means", CMT)], "miss"),
        ([("-- a NEW forward migration.", CMT)], "miss"),
        ([("-- undo is a paid Teams feature.", CMT)], "miss"),
    ]

    liquibase_code = [
        ([("<changeSet", TAG), (" id=", ATTR), ('"004-add-product-audit-table"', STR)], None),
        ([("            author=", ATTR), ('"wallaceespindola"', STR), (">", TAG)], None),
        ([("  <preConditions", TAG), (" onFail=", ATTR), ('"MARK_RAN"', STR), (">", TAG)], "have"),
        ([("    <tableExists", TAG), (" tableName=", ATTR), ('"product"', STR), ("/>", TAG)], "have"),
        ([("  </preConditions>", TAG)], "have"),
        ([("", TEXT)], None),
        ([("  <createTable", TAG), (" tableName=", ATTR), ('"product_audit"', STR), (">", TAG)], None),
        ([("    ...", CMT)], None),
        ([("  </createTable>", TAG)], None),
        ([("", TEXT)], None),
        ([("  <rollback>", TAG)], "have"),
        ([("    <dropTable", TAG), (" tableName=", ATTR), ('"product_audit"', STR), ("/>", TAG)], "have"),
        ([("  </rollback>", TAG)], "have"),
    ]

    def pane(x: float, title: str, colour: str, lines: list, badge: str) -> str:
        out = [f"""
  <rect x="{x}" y="{pane_y}" width="{pane_w}" height="{pane_h}" rx="{p(9)}" fill="{BG_INSET}"
        stroke="{BORDER}" stroke-width="{p(1.2)}"/>
  <path d="M {x} {pane_y + p(9)} a {p(9)} {p(9)} 0 0 1 {p(9)} {-p(9)} h {pane_w - p(18)}
           a {p(9)} {p(9)} 0 0 1 {p(9)} {p(9)} v {bar - p(9)} h {-pane_w} z" fill="{colour}"
        fill-opacity="0.16"/>
  <rect x="{x}" y="{pane_y}" width="{p(4)}" height="{pane_h}" rx="{p(2)}" fill="{colour}"/>
  <text x="{x + code_x_pad}" y="{pane_y + bar * 0.68}" font-family="{MONO}" font-size="{p(12)}"
        fill="{colour}" font-weight="700">{title}</text>
  <text x="{x + pane_w - code_x_pad}" y="{pane_y + bar * 0.68}" text-anchor="end"
        font-family="{SANS}" font-size="{p(11)}" fill="{MUTED}" letter-spacing="{p(1)}">{badge}</text>"""]

        for i, (segs, mark) in enumerate(lines):
            y = code_y0 + i * line_h
            if mark:
                tint, op = (OK, 0.10) if mark == "have" else (MUTED, 0.07)
                out.append(f'  <rect x="{x + p(8)}" y="{y - line_h * 0.72}" width="{pane_w - p(16)}" '
                           f'height="{line_h}" rx="{p(3)}" fill="{tint}" fill-opacity="{op}"/>')
            # The XML pane's code contains literal < and >, which would otherwise be parsed as SVG
            # markup and break the document.
            spans = "".join(
                f'<tspan fill="{c}">{t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</tspan>'
                for t, c in segs)
            out.append(f'  <text x="{x + code_x_pad}" y="{y}" font-family="{MONO}" '
                       f'font-size="{p(11.5)}" xml:space="preserve">{spans}</text>')
        return "".join(out)

    lx, rx_ = m, m + pane_w + gap
    foot_y = h - p(24)

    body = f"""
  <text x="{m}" y="{p(38)}" font-family="{SANS}" font-size="{p(25)}" font-weight="700"
        letter-spacing="{p(-0.4)}">
    <tspan fill="{FLYWAY}">Flyway</tspan><tspan fill="{MUTED}" dx="{p(8)}" font-size="{p(17)}"
      font-weight="400">vs</tspan><tspan fill="{LIQUIBASE}" dx="{p(8)}">Liquibase</tspan>
  </text>
  <text x="{m + p(255)}" y="{p(38)}" font-family="{SANS}" font-size="{p(15)}" fill="{MUTED}">
    the same migration, written twice</text>
  <text x="{w - m}" y="{p(38)}" text-anchor="end" font-family="{MONO}" font-size="{p(13)}"
        fill="{OK}">"schemasEquivalent": true</text>

{pane(lx, "V4__add_product_audit_table.sql", FLYWAY, flyway_code, "SQL")}
{pane(rx_, "004-add-product-audit-table.xml", LIQUIBASE, liquibase_code, "XML")}

  <text x="{w / 2}" y="{foot_y}" text-anchor="middle" font-family="{SANS}" font-size="{p(13)}"
        fill="{MUTED}">Identical result in the database — but only one side can declare how to undo
    itself.</text>
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
    "convergence": style_convergence,
    "editorial": style_editorial,
    "diff": style_diff,
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
