# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A Spring Boot application that compares **Flyway and Liquibase** by building the same logical schema
twice — once with each engine, against two independent H2 databases — and diffing the results at
runtime. The comparison is executable, not editorial: `/api/v1/comparison` reports
`schemasEquivalent` by reading both schemas back from `INFORMATION_SCHEMA`.

Original requirements: `docs/specs/PRD_Specs.md`.

## Commands

```bash
mvn verify                    # tests + JaCoCo gate (fails under 80% line coverage)
mvn test                      # tests only
mvn spring-boot:run           # foreground
make help                     # every Makefile target
./start.sh [--clean]          # build + background start + health wait (start.ps1 on Windows)
./stop.sh                     # stop (stop.ps1 on Windows)
make clean-db                 # rm -rf data/ so both engines re-migrate from scratch
```

Single test class / nested class / method:

```bash
mvn test -Dtest=ApplicationIntegrationTest
mvn test -Dtest='ApplicationIntegrationTest$CentralClaim'
mvn test -Dtest=ComparisonServiceTest#identicalSchemasHaveNoDifferences
```

Coverage report: `target/site/jacoco/index.html`. Currently 86 tests, 98.8% line / 82.5% branch
coverage against an 80% line gate — only `main()` is uncovered, by design.

## Architecture — the parts that matter

**Two DataSources, two databases, one schema.** `./data/flywaydb` is owned by Flyway;
`./data/liquibasedb` is owned by Liquibase. Neither engine ever touches the other's database. This is
the design decision the whole project rests on — if you collapse them into one database, the
comparison stops meaning anything.

**Both engines are wired explicitly, not auto-configured.** `DataSourceAutoConfiguration` is excluded
in `application.yml`. `FlywayConfig` and `LiquibaseConfig` each build their own
`DataSourceProperties` → `DataSource` → engine → `JdbcTemplate` chain. This is deliberate: the point
of the demo is showing what bootstrapping each tool actually takes. Do not "simplify" it back to
`spring.flyway.*` / `spring.liquibase.*` auto-configuration.

**Migration ordering is enforced by `@DependsOn`.** `flywayJdbcTemplate` depends on the `flyway` bean
(which migrates via `initMethod = "migrate"`); `liquibaseJdbcTemplate` depends on the `liquibase`
bean (which migrates in `afterPropertiesSet()`). Removing those annotations introduces a race where a
query can hit an unmigrated database.

**Qualifiers everywhere.** With two of every JDBC bean, `@Qualifier` is not optional. The constants
`FlywayConfig.JDBC_TEMPLATE` and `LiquibaseConfig.JDBC_TEMPLATE` exist so qualifier strings are not
duplicated as literals. The Flyway side is `@Primary`.

**`MigrationHistoryProvider` has two genuinely different implementations**, and the difference is a
finding, not an accident: `FlywayHistoryService` reads history through Flyway's `info()` API;
`LiquibaseHistoryService` must `SELECT` from `DATABASECHANGELOG` because Liquibase exposes no
equivalent embedded read API. Keep both shapes — collapsing them would erase the point.

### Migration files are a paired translation table

Every Flyway migration has a deliberate Liquibase counterpart:

| Flyway `db/migration/` | Liquibase `db/changelog/changes/` | Demonstrates |
| --- | --- | --- |
| `V1__create_category_table.sql` | `001-…​.xml` | Raw SQL vs portable XML tags |
| `V2__create_product_table.sql` | `002-…​.yaml` | YAML format + `<sql>` escape hatch |
| `V3__seed_reference_data.sql` | `003-…​.sql` | Liquibase SQL-formatted changelog |
| `V4__add_product_audit_table.sql` | `004-…​.xml` | Preconditions + `<rollback>` |
| `V5__add_product_active_flag.sql` | `005-…​.xml` | Contexts/labels; split into `005` + `005b` |
| `R__product_catalog_view.sql` | `006-…​.xml` | `R__` vs `runOnChange="true"` |

**If you add a migration to one side, add its counterpart to the other**, or the equivalence assertion
in `ApplicationIntegrationTest.CentralClaim` will fail — which is exactly what it is there for.

Applied counts are **6 (Flyway) vs 7 (Liquibase)** on purpose: `005` splits the column addition and
the backfill into two rollback-able changesets where `V5` does both in one script. Tests assert these
exact numbers, so update them together.

## Gotchas that have already bitten

- **Liquibase XML element order is schema-enforced.** `<preConditions>` must come *before*
  `<comment>` and the change tags, or the changelog fails to parse at startup. This cost a debug cycle
  already.
- **`ComparisonService.diff()` deliberately ignores indexes.** H2 auto-generates constraint-backing
  indexes under names that differ between engines. Adding indexes to the comparison would report noise
  as drift. Tables, views and columns are compared.
- **MockMvc does not follow forwards.** `GET /` is a welcome-page *forward* to `index.html`, so assert
  `forwardedUrl("index.html")` there and assert body content against `/index.html` directly.
- **Tests use `@ActiveProfiles("test")` with `application-test.yml`**, which overrides only the two
  JDBC URLs to in-memory H2. Do not add a `src/test/resources/application.yml` — a same-named file
  would shadow the main config entirely instead of merging.
- **Spring Boot 4 moved auto-config into per-technology modules.** `DataSourceProperties` /
  `DataSourceAutoConfiguration` live in `org.springframework.boot.jdbc.autoconfigure`, MockMvc test
  support needs `spring-boot-starter-webmvc-test`, and the H2 console needs `spring-boot-h2console`.
  Jackson is 3.x (`tools.jackson`): ISO dates are the default, so `write-dates-as-timestamps` no longer
  binds — `spring.jackson.default-property-inclusion: always` keeps the nulls.
- Flyway logs a warning that H2 2.4.240 is newer than it has been tested against. Both versions come
  from the Spring Boot 4.1.2 BOM and everything works; pinning either one takes the project off
  Spring Boot's tested set.

## Frontend

Vanilla HTML/CSS/JS in `src/main/resources/static` — no framework, no build step. `js/app.js` builds
markup with template literals, so **every string from the API must go through the `esc()` helper**
and every numeric interpolation through `num()`. That is the single XSS choke point.

## Conventions

- Java 25, Spring Boot 4.1.2, plain JDBC (no JPA). Max line length 120.
- Engine versions come from the Spring Boot BOM, not from `<properties>`: **Flyway 12.4.0**,
  **Liquibase 5.0.4**, **H2 2.4.240**. Leave them unpinned — letting the BOM pick both keeps the
  comparison fair, and the README badges state these numbers, so re-run
  `mvn dependency:list | grep -E 'flyway-core|liquibase-core|h2'` and update the badges if the
  Spring Boot version ever moves.
- DTOs are **records**; services and controllers use **Lombok** (`@Slf4j`, `@RequiredArgsConstructor`).
- Every REST response is wrapped in `ApiResponse<T>` with a server `timestamp`. Jackson keeps nulls on
  purpose — `"executionTimeMs": null` for Liquibase is a *finding*, not an absence to hide.
- Author attribution (Wallace Espindola, GitHub/LinkedIn) belongs in `pom.xml`, the README Author
  section and file-level Javadoc.

## Documentation

This repo is also the single source for an article series (one repo per series):

- `docs/articles/<platform>/article.md` for devto, dzone, infoq, javapro, linkedin, medium, substack, each
  with `banner.png` (InfoQ and JavaPro have none yet) and `diagrams/` (source next to PNG; DZone and
  InfoQ embed JPEG copies). Banner layouts: `docs/articles/BANNER_DESIGN.md`, `generate_banners.py`.
- `docs/presentations/slides.md` is the Marp deck source; `make slides` exports the Speaker Deck PDF
  and rebuilds the editable PPTX via `build_pptx.py`. Import steps: `GOOGLE_SLIDES.md`.
- `docs/diagrams/` holds the canonical PlantUML/Mermaid sources (`make diagrams` renders all).
  Article and slide copies of `two-engines-overview` and `paired-migrations` are derived from here.
- `docs/glossary.md` lists every acronym used in the docs; add new ones there.
- `make docs-check` runs markdownlint and the offline link check, as `.github/workflows/docs.yml` does.

Code snippets in articles are copied from the repo: change code first, then the articles. Articles
use no em dashes, sentence-case headings (InfoQ title excepted) and end with the "Examples repository"
line plus the fixed footer. The repo README must not list or link the articles.

When changing behaviour, the docs that make **specific factual claims** (the feature matrix, the
applied counts, the diagram contents, the versions in articles and slides) need updating alongside the code.
