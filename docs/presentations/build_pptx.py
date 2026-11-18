"""Build the editable PPTX (Google Slides import) from the Marp source slides.md.

Same palette as the Marp theme; Arial and Courier New only so Google Slides keeps the layout.
Usage: uv run --with python-pptx --with pillow docs/presentations/build_pptx.py
Writes docs/presentations/<repo-name>.pptx; the footer comes from the `footer:` in the Marp front matter.
"""

import re
import shutil
import zipfile
from pathlib import Path

from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt

HERE = Path(__file__).parent
SRC = HERE / "slides.md"
OUT = HERE / f"{HERE.resolve().parents[1].name}.pptx"
FOOTER_MATCH = re.search(r"^footer:\s*'?([^'\n]*)'?", SRC.read_text(), re.MULTILINE)
FOOTER = FOOTER_MATCH.group(1) if FOOTER_MATCH else ""

INK = RGBColor(0x0B, 0x15, 0x30)
TEXT = RGBColor(0x1E, 0x29, 0x3B)
MUTED = RGBColor(0x64, 0x74, 0x8B)
TEAL = RGBColor(0x14, 0xB8, 0xA6)
TEAL_DARK = RGBColor(0x0F, 0x76, 0x6E)
TEAL_SOFT = RGBColor(0xEC, 0xFD, 0xF9)
INDIGO = RGBColor(0x63, 0x66, 0xF1)
BG = RGBColor(0xF8, 0xFA, 0xFC)
CODE_BG = RGBColor(0x0F, 0x17, 0x2A)
CODE_FG = RGBColor(0xE2, 0xE8, 0xF0)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
ZEBRA = RGBColor(0xF1, 0xF5, 0xF9)
LEAD_BG = RGBColor(0x0F, 0x1A, 0x40)
LEAD_SUB = RGBColor(0xCB, 0xD5, 0xE1)
LEAD_TITLE = RGBColor(0x99, 0xF6, 0xE4)

W, H = Inches(13.333), Inches(7.5)
LEFT, RIGHT_EDGE = Inches(0.85), Inches(13.333 - 0.85)
CONTENT_W = RIGHT_EDGE - LEFT


def parse(md: str):
    body = re.sub(r"\A---\n[\s\S]*?\n---\n", "", md)
    slides = []
    for raw in re.split(r"\n---\n", body):
        notes = "\n".join(m.strip() for m in re.findall(r"<!--\s*(?!_)([\s\S]*?)-->", raw)
                          if not m.strip().startswith("_"))
        cls = re.search(r"<!--\s*_class:\s*(\w+)", raw)
        text = re.sub(r"<!--[\s\S]*?-->", "", raw).strip()
        slides.append({"cls": cls.group(1) if cls else "", "md": text, "notes": notes})
    return slides


def blocks(md: str):
    """Yield (kind, payload) blocks: h1, h2, ul, ol, p, table, code, img."""
    lines = md.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
        elif line.startswith("```"):
            j = i + 1
            while not lines[j].startswith("```"):
                j += 1
            yield "code", "\n".join(lines[i + 1:j])
            i = j + 1
        elif line.startswith("# "):
            yield "h1", line[2:].strip()
            i += 1
        elif line.startswith("## "):
            yield "h2", line[3:].strip()
            i += 1
        elif line.startswith("!["):
            yield "img", re.search(r"\((.+?)\)", line).group(1)
            i += 1
        elif line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = [c.strip() for c in lines[i].strip("|").split("|")]
                if not all(re.fullmatch(r"-+", c) for c in cells):
                    rows.append(cells)
                i += 1
            yield "table", rows
        elif re.match(r"(- |\d+\. )", line):
            ordered = bool(re.match(r"\d+\. ", line))
            items = []
            while i < len(lines) and re.match(r"(- |\d+\. )", lines[i]):
                items.append(re.sub(r"^(- |\d+\. )", "", lines[i]))
                i += 1
            yield ("ol" if ordered else "ul"), items
        else:
            para = []
            while i < len(lines) and lines[i].strip() and not re.match(r"(- |\d+\. |#|\||```|!\[)", lines[i]):
                para.append(lines[i].strip())
                i += 1
            yield "p", "\n".join(para)


def runs(paragraph, text, size, color, bold_color=None, font="Arial"):
    """Add Markdown inline runs (**bold**, `code`, [link](url)) to a paragraph."""
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"<((?:https?://|[^\s>]+@)[^>]+)>", r"\1", text)
    for part in re.split(r"(\*\*[^*]+\*\*|`[^`]+`)", text):
        if not part:
            continue
        r = paragraph.add_run()
        f = r.font
        f.size, f.name, f.color.rgb = Pt(size), font, color
        if part.startswith("**"):
            r.text, f.bold = part[2:-2], True
            f.color.rgb = bold_color or color
        elif part.startswith("`"):
            r.text, f.name = part[1:-1], "Courier New"
            f.color.rgb = TEAL_DARK if color != WHITE else LEAD_TITLE
        else:
            r.text = part


def rect(slide, x, y, w, h, fill, shape=MSO_SHAPE.RECTANGLE):
    s = slide.shapes.add_shape(shape, x, y, w, h)
    s.fill.solid()
    s.fill.fore_color.rgb = fill
    s.line.fill.background()
    s.shadow.inherit = False
    return s


def textbox(slide, x, y, w, h):
    tb = slide.shapes.add_textbox(x, y, w, h)
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    return tb, tf


def background(slide, color):
    fill = slide.background.fill
    fill.solid()
    fill.fore_color.rgb = color


def lead_slide(slide, md):
    background(slide, LEAD_BG)
    rect(slide, Inches(9.5), 0, Inches(3.833), H, RGBColor(0x13, 0x3A, 0x55))
    rect(slide, Inches(1.2), Inches(1.55), Inches(1.1), Inches(0.1), TEAL)
    y = Inches(1.9)
    first_p = True
    for kind, payload in blocks(md):
        if kind == "h1":
            _, tf = textbox(slide, Inches(1.2), y, Inches(10.5), Inches(1.2))
            runs(tf.paragraphs[0], payload, 54, WHITE)
            tf.paragraphs[0].runs[0].font.bold = True
            y += Inches(1.15)
        elif kind == "h2":
            _, tf = textbox(slide, Inches(1.2), y, Inches(10.5), Inches(0.7))
            runs(tf.paragraphs[0], payload, 26, LEAD_SUB)
            y += Inches(1.0)
        elif kind == "p":
            _, tf = textbox(slide, Inches(1.2), y, Inches(10.5), Inches(1.2))
            for k, line in enumerate(payload.split("\n")):
                para = tf.paragraphs[0] if k == 0 else tf.add_paragraph()
                color = LEAD_TITLE if ("github.com" in line and "|" not in line) or "@" in line else LEAD_SUB
                runs(para, line, 18 if first_p else 16, color, bold_color=WHITE)
            y += Inches(0.32 * payload.count("\n") + 0.5)
            first_p = False


def chrome(slide, number):
    background(slide, BG)
    rect(slide, Inches(11.4), 0, Inches(1.933), Inches(0.12), TEAL)
    _, tf = textbox(slide, LEFT, Inches(7.0), Inches(6), Inches(0.3))
    runs(tf.paragraphs[0], FOOTER, 11, MUTED)
    pill = rect(slide, Inches(12.0), Inches(6.93), Inches(0.5), Inches(0.32), INK, MSO_SHAPE.ROUNDED_RECTANGLE)
    ptf = pill.text_frame
    ptf.margin_left = ptf.margin_right = ptf.margin_top = ptf.margin_bottom = 0
    ptf.vertical_anchor = MSO_ANCHOR.MIDDLE
    p = ptf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    runs(p, str(number), 11, WHITE)
    p.runs[0].font.bold = True


def content_slide(slide, md, cls, number):
    chrome(slide, number)
    y = Inches(0.55)
    prev = None
    for kind, payload in blocks(md):
        if kind == "h1":
            rect(slide, LEFT, y, Inches(0.75), Inches(0.07), TEAL)
            _, tf = textbox(slide, LEFT, y + Inches(0.2), CONTENT_W, Inches(0.8))
            runs(tf.paragraphs[0], payload, 32, INK)
            tf.paragraphs[0].runs[0].font.bold = True
            y += Inches(1.25)
        elif kind in ("ul", "ol"):
            size = 18 if kind == "ol" else (20 if len(payload) <= 5 else 18)
            step = Inches(0.47 if size == 20 else 0.44)
            for n, item in enumerate(payload, 1):
                if kind == "ol":
                    dot = rect(slide, LEFT, y + Inches(0.03), Inches(0.32), Inches(0.32), INK, MSO_SHAPE.OVAL)
                    dtf = dot.text_frame
                    dtf.margin_left = dtf.margin_right = dtf.margin_top = dtf.margin_bottom = 0
                    dtf.vertical_anchor = MSO_ANCHOR.MIDDLE
                    dtf.paragraphs[0].alignment = PP_ALIGN.CENTER
                    runs(dtf.paragraphs[0], str(n), 12, WHITE)
                else:
                    rect(slide, LEFT + Inches(0.03), y + Inches(0.12), Inches(0.13), Inches(0.13), TEAL,
                         MSO_SHAPE.ROUNDED_RECTANGLE)
                indent = Inches(0.5 if kind == "ol" else 0.38)
                _, tf = textbox(slide, LEFT + indent, y, CONTENT_W - indent, step)
                runs(tf.paragraphs[0], item, size, TEXT, bold_color=TEAL_DARK)
                lines = max(1, round(len(re.sub(r"[*`]", "", item)) / (95 if size == 18 else 85) + 0.45))
                y += step + Inches(0.3) * (lines - 1)
            y += Inches(0.1)
        elif kind == "p" and prev in ("ul", "ol", "table"):
            h = Inches(0.62 + 0.3 * (len(payload) // 100))
            rect(slide, LEFT, y + Inches(0.1), CONTENT_W, h, TEAL_SOFT)
            rect(slide, LEFT, y + Inches(0.1), Inches(0.08), h, TEAL)
            _, tf = textbox(slide, LEFT + Inches(0.3), y + Inches(0.1), CONTENT_W - Inches(0.5), h)
            tf.vertical_anchor = MSO_ANCHOR.MIDDLE
            runs(tf.paragraphs[0], payload, 19, INK, bold_color=TEAL_DARK)
            y += h + Inches(0.3)
        elif kind == "p":
            centered = cls == "diagram"
            _, tf = textbox(slide, LEFT, y, CONTENT_W, Inches(0.5))
            para = tf.paragraphs[0]
            if centered:
                para.alignment = PP_ALIGN.CENTER
            caption = prev == "code"
            runs(para, payload.replace("\n", " "), 13 if caption else 20, MUTED if caption else INK,
                 bold_color=TEAL_DARK)
            y += Inches(0.5)
        elif kind == "code":
            n = payload.count("\n") + 1
            widest = max(len(line) for line in payload.split("\n"))
            size = 11 if n > 18 or widest > 92 else (12 if n > 16 or widest > 84 else 14)
            h = Inches(0.23 * n * size / 12 + 0.45)
            rect(slide, LEFT, y, CONTENT_W, h, CODE_BG, MSO_SHAPE.ROUNDED_RECTANGLE).adjustments[0] = 0.04
            _, tf = textbox(slide, LEFT + Inches(0.35), y + Inches(0.2), CONTENT_W - Inches(0.7), h - Inches(0.4))
            for k, line in enumerate(payload.split("\n")):
                para = tf.paragraphs[0] if k == 0 else tf.add_paragraph()
                r = para.add_run()
                r.text = line or " "
                r.font.name, r.font.size, r.font.color.rgb = "Courier New", Pt(size), CODE_FG
            y += h + Inches(0.12)
        elif kind == "table":
            rows, cols = len(payload), len(payload[0])
            first = Inches(4.0 if cols == 2 else 3.4)
            widths = [first] + [int((CONTENT_W - first) / (cols - 1))] * (cols - 1)
            # ponytail: ~8.5 chars per inch at 15pt Arial; rows grow by line count, not by measured text
            heights = [Inches(0.4 + 0.26 * (max(
                -(-len(re.sub(r"[*`]", "", v)) // int(widths[c] / 914400 * 8.5)) for c, v in enumerate(row)) - 1))
                for row in payload]
            shape = slide.shapes.add_table(rows, cols, LEFT, y, CONTENT_W, sum(heights))
            table = shape.table
            for c, w in enumerate(widths):
                table.columns[c].width = w
            for r, row in enumerate(payload):
                table.rows[r].height = heights[r]
                for c, value in enumerate(row):
                    cell = table.cell(r, c)
                    cell.fill.solid()
                    cell.fill.fore_color.rgb = INK if r == 0 else (ZEBRA if r % 2 == 0 else WHITE)
                    cell.margin_left = cell.margin_right = Inches(0.18)
                    cell.margin_top = cell.margin_bottom = Inches(0.04)
                    cell.vertical_anchor = MSO_ANCHOR.MIDDLE
                    para = cell.text_frame.paragraphs[0]
                    color = WHITE if r == 0 else (INK if c == 0 else TEXT)
                    runs(para, value, 15, color)
                    if r == 0 or c == 0:
                        for run in para.runs:
                            run.font.bold = True
            y += sum(heights) + Inches(0.1)
        elif kind == "img":
            path = HERE / payload
            iw, ih = Image.open(path).size
            max_w, max_h = CONTENT_W, Inches(7.5 - 0.95) - y - Inches(0.9 if cls == "diagram" else 0.6)
            scale = min(max_w / iw, max_h / ih)
            w, h = int(iw * scale), int(ih * scale)
            x = int((W - w) / 2)
            card = rect(slide, x - Inches(0.15), y - Inches(0.1), w + Inches(0.3), h + Inches(0.2), WHITE,
                        MSO_SHAPE.ROUNDED_RECTANGLE)
            card.adjustments[0] = 0.04
            card.line.color.rgb = RGBColor(0xE2, 0xE8, 0xF0)
            slide.shapes.add_picture(str(path), x, y, w, h)
            y += h + Inches(0.3)
        prev = kind


def fix_notes_master(path: Path) -> None:
    """Keynote rejects python-pptx files with notes unless presentation.xml lists the notes master."""
    tmp = path.with_suffix(".tmp")
    with zipfile.ZipFile(path) as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
        rels = src.read("ppt/_rels/presentation.xml.rels").decode()
        rid = re.search(r'Id="(rId\d+)"[^>]*Target="notesMasters/', rels) or re.search(
            r'Target="notesMasters/[^"]*"[^>]*Id="(rId\d+)"', rels)
        for item in src.infolist():
            data = src.read(item.filename)
            if item.filename == "ppt/presentation.xml" and rid and b"notesMasterIdLst" not in data:
                tag = f'<p:notesMasterIdLst><p:notesMasterId r:id="{rid.group(1)}"/></p:notesMasterIdLst>'
                data = data.replace(b"</p:sldMasterIdLst>", b"</p:sldMasterIdLst>" + tag.encode())
            dst.writestr(item, data)
    shutil.move(tmp, path)


def main():
    prs = Presentation()
    prs.slide_width, prs.slide_height = W, H
    blank = prs.slide_layouts[6]
    for number, s in enumerate(parse(SRC.read_text()), 1):
        slide = prs.slides.add_slide(blank)
        if s["cls"] == "lead":
            lead_slide(slide, s["md"])
        else:
            content_slide(slide, s["md"], s["cls"], number)
        if s["notes"]:
            slide.notes_slide.notes_text_frame.text = s["notes"]
    prs.save(OUT)
    fix_notes_master(OUT)
    print(f"{OUT.name}: {len(prs.slides)} slides")


if __name__ == "__main__":
    main()
