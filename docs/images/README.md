# Images

## Article banners

One banner per publishing platform, each rendered at that platform's native cover ratio so the CMS
crops as little as possible.

Each banner uses a **different layout**, not one template recoloured — five near-identical images
read as a copy-paste job when the articles are seen side by side. The shared palette is what keeps
them recognisably one project.

| File | Size | Style | Used by |
| --- | --- | --- | --- |
| `banner-dzone.png` | 1200×628 | **split panel** — the two engines face off across a divider | [DZone](../articles/dzone-flyway-vs-liquibase.md) |
| `banner-medium.png` | 1400×700 | **editorial** — centred serif, framed, magazine restraint | [Medium](../articles/medium-flyway-vs-liquibase.md) |
| `banner-devto.png` | 1000×420 | **terminal** — a shell session running the real curl | [Dev.to](../articles/devto-flyway-vs-liquibase.md) |
| `banner-linkedin.png` | 1280×720 | **data card** — left-aligned headline, stat block | [LinkedIn](../articles/linkedin-flyway-vs-liquibase.md) |
| `banner-substack.png` | 1200×600 | **typographic** — newsletter masthead, rules and whitespace | [Substack](../articles/substack-flyway-vs-liquibase.md) |

![The Dev.to banner: a terminal window showing ./start.sh, the applied counts, and the comparison
endpoint returning schemasEquivalent true](banner-devto.png)

The palette is taken from the dashboard stylesheet (`src/main/resources/static/css/styles.css`) —
slate background, Flyway orange `#f2784b`, Liquibase teal `#35c4d0` — so the articles and the running
application read as one project. Every number shown is real: 6 applied migrations, 7 applied
changesets, 0 schema differences, and `"schemasEquivalent": true` is the actual field the comparison
endpoint returns.

The Dev.to article carries its banner **twice on purpose**: the `cover_image` front-matter field is
what Dev.to renders as the article cover, and an inline copy is what makes the image visible when the
`.md` file is read on GitHub. Dev.to shows both; that is the accepted cost of the file being readable
in the repo.

### Gotchas hit while building these

- SVG collapses whitespace at `tspan` boundaries, so `"applied "` + `"6 migrations"` renders as
  `applied6 migrations`. The terminal rows set `xml:space="preserve"`; elsewhere the gap comes from
  an explicit `dx`, because changing `font-size` on a `tspan` adds no advance width.
- A gradient **stroke** on a horizontal `<line>` never paints — the object bounding box has zero
  height, so `url(#rule)` cannot resolve. Use a thin `<rect>` instead.
- Radial gradients need `cx="0.5" cy="0.5" r="0.5"`. Anchored at `0,0` they are centred on the
  bounding-box corner, never reach zero opacity, and the shape's edge shows as a hard arc.

### Regenerating

Both the `.svg` sources and the `.png` output are generated, and both are committed so the repo does
not need the toolchain just to render. To change them, edit `generate_banners.py` and re-run it:

```bash
python3 docs/images/generate_banners.py
```

Requires `rsvg-convert` (librsvg):

```bash
brew install librsvg          # macOS
sudo apt install librsvg2-bin # Debian / Ubuntu
```

To add a platform, append a `Banner(...)` entry to the `BANNERS` list — the layout scales from a
1200×600 reference design, so any reasonable ratio works without hand-tuning.

## Screenshots

| File | What it shows |
| --- | --- |
| `dashboard.png` | The comparison dashboard at <http://localhost:8080/>, used in the project [README](../../README.md) |

Retake it by running the app (`./start.sh`) and capturing the page at a wide viewport.

---

Author: **Wallace Espindola** — [GitHub](https://github.com/wallaceespindola/) ·
[LinkedIn](https://www.linkedin.com/in/wallaceespindola/)
