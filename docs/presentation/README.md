# Presentation materials

Talk materials for **Flyway vs Liquibase — a measured comparison**, built entirely from the code in
this repository. Every snippet is real, and the headline result (`schemasEquivalent: true`) comes
from running the application, not from an estimate.

Author: **Wallace Espindola**, Senior Software Engineer & Solution Architect ·
[github.com/wallaceespindola](https://github.com/wallaceespindola) ·
[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)

---

## Artefacts

| File | What it is |
|---|---|
| [`flyway-vs-liquibase-slides.md`](flyway-vs-liquibase-slides.md) | The deck as Markdown — 24 slides separated by `---`, speaker notes in HTML comment blocks under each slide. Readable on GitHub, usable with Marp/reveal.js/Slidev. |
| [`flyway-vs-liquibase-deck.pptx`](flyway-vs-liquibase-deck.pptx) | The same deck as a real PowerPoint file: 24 slides, 16:9, dark slate theme, speaker notes on every slide. Ready to present or to import into Google Slides. |
| [`generate_pptx.py`](generate_pptx.py) | The generator that produces the `.pptx`. This is the source of truth for the deck — edit it rather than the binary. |
| [`google-slides-script.md`](google-slides-script.md) | Delivery script: a per-slide talk track (60–90 seconds each), timing plans for a 30-minute and a 45-minute slot, anticipated Q&A, and instructions for importing into Google Slides. |

## Deck outline (24 slides)

1. Title
2. The problem: schema drift and manual DDL
3. Why migration tooling
4. How Flyway works
5. How Liquibase works
6. The experiment design — two databases, one schema
7. Flyway migrations walkthrough
8. Flyway code: expand/contract and repeatable
9. Liquibase changelog walkthrough
10. Liquibase code: preconditions and rollback
11. Side by side: the same table, two ways
12. Bookkeeping: what each engine records
13. Reading the history: two different APIs
14. **The measured result** — `schemasEquivalent: true`
15. Feature matrix 1 of 3 — authoring
16. Feature matrix 2 of 3 — execution
17. Feature matrix 3 of 3 — operations
18. The rollback story
19. The portability story
20. Review and merge-conflict ergonomics
21. **Decision guide** — choose Flyway when / choose Liquibase when
22. Architecture of the demo app
23. How to run it
24. Questions

## Regenerating the PowerPoint

Requires Python 3 and [`python-pptx`](https://python-pptx.readthedocs.io/):

```bash
pip install python-pptx          # if not already installed
python3 docs/presentation/generate_pptx.py
```

The script writes `docs/presentation/flyway-vs-liquibase-deck.pptx`, reopens it, asserts the slide
count and that every slide carries speaker notes, then prints the path, slide count and file size:

```
.../flyway-vs-liquibase-deck.pptx  24 slides  108,587 bytes
```

### Editing the deck

Edit `generate_pptx.py` and re-run it. Do not edit the `.pptx` by hand — the next regeneration
overwrites it.

Each slide is one function at the bottom of the script, registered in the `SLIDES` list. To add,
remove or reorder slides, change that list. Shared helpers sit at the top:

| Helper | Use |
|---|---|
| `new_slide(prs, notes)` | blank slide with the themed background and speaker notes |
| `heading(slide, title, accent=…)` | slide title plus the coloured rule underneath |
| `bullets(slide, items, …)` | bulleted body text — keep to about 6 items to avoid overflow |
| `code(slide, lines, top=…, caption=…)` | monospace code panel; returns its bottom edge so panels can be stacked |
| `table(slide, headers, rows, …)` | themed table; `"Flyway"`, `"Liquibase"` and `"Tie"` cells are auto-coloured |
| `footer(slide)` | the running footer |

Theme constants are declared once near the top of the file:

- Background `#1E293B` (dark slate), body text `#E2E8F0`, muted `#94A3B8`
- Accent `#FBBF24` (amber) for headings, rules and emphasis
- **Flyway `#F43F5E`** (rose) and **Liquibase `#22D3EE`** (cyan), used consistently on every slide
- Code panels `#0F172A` with `Consolas`; body text in `Calibri`

Keeping the Flyway/Liquibase colours fixed is what lets the audience track which side of the
comparison they are looking at without reading a label.

## Keeping the deck honest

If the application changes, these numbers must be re-checked before presenting:

| Claim on the deck | Where it comes from |
|---|---|
| 6 Flyway migrations applied | `src/main/resources/db/migration/` (V1–V5 + `R__`) |
| 7 Liquibase changesets applied | `src/main/resources/db/changelog/` (005 contains two) |
| 18-row feature matrix | `service/FeatureMatrix.java` |
| `schemasEquivalent: true`, zero differences | `GET /api/v1/comparison` on the running app |
| Bookkeeping table columns | `service/FlywayHistoryService.java`, `service/LiquibaseHistoryService.java` |
| Endpoint list | `controller/*.java` |

Run the app and confirm before a talk:

```bash
mvn spring-boot:run
curl -s localhost:8080/api/v1/comparison | grep -o '"schemasEquivalent":[a-z]*'
```
