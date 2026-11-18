# Getting this deck into Google Slides, Keynote and Speaker Deck

The talk is **Flyway vs Liquibase: Same Schema, Two Engines, One Measured Diff** (25 slides, 16:9, speaker notes on every slide).

| File | What it is |
|---|---|
| `slides.md` | The source: a [Marp](https://marp.app) deck, speaker notes as HTML comments |
| `flyway-vs-liquibase-db-migrations.pptx` | Editable PPTX for Google Slides, PowerPoint and Keynote, built from `slides.md` |
| `flyway-vs-liquibase-db-migrations.pdf` | PDF export of the Marp deck, for Speaker Deck |
| `build_pptx.py` | Builds the PPTX from `slides.md` (Arial and Courier New only, notes included) |
| `diagrams/` | Mermaid sources (`.mmd`) and the PNGs the deck uses |

Edit `slides.md`, then rebuild both exports. Don't edit the PPTX or PDF by hand.

## Step 0: Get the file

- From a clone: `docs/presentations/flyway-vs-liquibase-db-migrations.pptx`.
- From GitHub without cloning: open [the file on GitHub](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations/blob/main/docs/presentations/flyway-vs-liquibase-db-migrations.pptx) and click **Download raw file**.

## Option 1: Open it as a new Google Slides presentation

1. Go to [Google Slides](https://slides.google.com).
2. Click **Blank presentation**, then **File > Open** (or press Ctrl+O / Cmd+O).
3. Choose the **Upload** tab and drop `flyway-vs-liquibase-db-migrations.pptx` in.
4. When it opens, use **File > Save as Google Slides** so you edit a native copy, not the PPTX.

## Option 2: Add the slides to a deck you already have

1. Open your existing presentation in Google Slides.
2. Go to **File > Import slides**.
3. Choose the **Upload** tab and select `flyway-vs-liquibase-db-migrations.pptx`.
4. Pick **All** (or only the slides you want). Leave **Keep original theme** checked so the dark title slides and colors stay as designed.
5. Click **Import slides**. They're added after the slide you have selected.

## Option 3: Through Google Drive

1. Upload `flyway-vs-liquibase-db-migrations.pptx` to [Google Drive](https://drive.google.com) (**New > File upload**).
2. Right-click the file and choose **Open with > Google Slides**.
3. Use **File > Save as Google Slides** to convert it.

To have every PPTX convert automatically on upload, turn on **Drive settings > Convert uploads to Google Docs editor format**.

## After importing

- Speaker notes come along: **View > Show speaker notes**.
- The deck uses only Arial and Courier New, so fonts don't get substituted.
- Diagrams are PNG images, so they look the same as in PowerPoint.
- Check the table slides (14, 17 and 24) once; row heights can shift slightly.
- To present: **Slideshow** (top right), or **Presenter view** from its dropdown to see your notes.

## Keynote

Open `flyway-vs-liquibase-db-migrations.pptx` in Keynote (**File > Open**). All 25 slides and the speaker notes come through.

## Speaker Deck

Speaker Deck takes PDF only. Sign in at [speakerdeck.com](https://speakerdeck.com), click **New Deck**, upload `flyway-vs-liquibase-db-migrations.pdf`, add a title and description, then publish. Upload Marp's native 960x540 PDF as is; don't upscale it.

## Rebuilding the exports

Run from the repository root:

```bash
# PDF (the Speaker Deck file)
npx -y @marp-team/marp-cli docs/presentations/slides.md --pdf --allow-local-files \
  -o docs/presentations/flyway-vs-liquibase-db-migrations.pdf </dev/null

# Editable PPTX with speaker notes (Google Slides, PowerPoint, Keynote)
uv run --with python-pptx --with pillow docs/presentations/build_pptx.py

# HTML preview
npx -y @marp-team/marp-cli docs/presentations/slides.md --allow-local-files -o docs/presentations/slides.html </dev/null
```

`--allow-local-files` lets Marp embed the diagram PNGs. If Marp can't find a browser, point it at Chrome with `CHROME_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"`. The `</dev/null` stops marp-cli waiting on stdin when it runs outside a terminal.

## Diagrams

| Diagram | Source | Render |
|---|---|---|
| Two engines, two databases | `diagrams/two-engines-overview.mmd` | `mmdc -i two-engines-overview.mmd -o two-engines-overview.png -b white -s 2` |
| Paired migrations, 6 vs 7 | `diagrams/paired-migrations.mmd` | `mmdc -i paired-migrations.mmd -o paired-migrations.png -b white -s 2` |

`paired-migrations.mmd` here is a landscape layout of the same pairing used in the articles, so it fits a 16:9 slide.

---

Author: Wallace Espindola, Solution Architect, <wallace.espindola@gmail.com>
LinkedIn: <https://www.linkedin.com/in/wallaceespindola/> | GitHub: <https://github.com/wallaceespindola/>
Repo: <https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations>
