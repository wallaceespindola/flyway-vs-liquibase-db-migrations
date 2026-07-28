# Flyway vs Liquibase — Database Migrations Compared

[![CI](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations/actions/workflows/ci.yml/badge.svg)](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Flyway 10.20.1](https://img.shields.io/badge/Flyway-10.20.1-CC0000.svg)](https://documentation.red-gate.com/flyway)
[![Liquibase 4.29.2](https://img.shields.io/badge/Liquibase-4.29.2-2962FF.svg)](https://docs.liquibase.com/)
[![H2 2.3.232](https://img.shields.io/badge/H2-2.3.232-0000BB.svg)](https://www.h2database.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> Both engine versions come from the Spring Boot 3.4.2 dependency BOM rather than being pinned here,
> so neither tool gets an unfair version advantage in the comparison.

Most Flyway-vs-Liquibase comparisons are opinion pieces. This one is an application.

It builds **the same logical schema twice** — once with Flyway, once with Liquibase — against **two
independent H2 databases**, then reads both back from `INFORMATION_SCHEMA` and diffs them at runtime.
The result is served through a REST API and a dashboard, so the comparison is something you can run
and check rather than something you have to take on faith.

The headline result, produced by the app itself:

```json
{
  "schemasEquivalent": true,
  "schemaDifferences": [],
  "flyway":    { "appliedCount": 6, "historyTable": "flyway_schema_history" },
  "liquibase": { "appliedCount": 7, "historyTable": "DATABASECHANGELOG" }
}
```

Both tools land on an identical business schema. Everything that differs — bookkeeping, metadata,
rollback support, ergonomics — is what the rest of this project is about.

## The dashboard

![The Flyway vs Liquibase comparison dashboard, showing zero structural differences between the two
schemas, the per-engine summary cards, and the normalised applied-migration
tables](docs/images/dashboard.png)

Served at <http://localhost:8080/> by the same Spring Boot process — plain HTML, CSS and JavaScript,
no framework and no build step. Every number on the page is read live from the REST API.

---

## Quick start

One command, no database to install. H2 runs embedded and both engines migrate on startup.

```bash
git clone https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations.git
cd flyway-vs-liquibase-db-migrations

./start.sh                  # Linux / macOS
# .\start.ps1               # Windows PowerShell
# make start                # or via the Makefile
# docker compose up --build # or via Docker
```

Then open **<http://localhost:8080/>**.

| URL | What it is |
| --- | --- |
| <http://localhost:8080/> | The comparison dashboard |
| <http://localhost:8080/swagger-ui.html> | Full REST API documentation |
| <http://localhost:8080/api/v1/comparison> | The raw comparison payload |
| <http://localhost:8080/h2-console> | Query either database directly |
| <http://localhost:8080/actuator/health> | Actuator health |

Stop it with `./stop.sh` (or `.\stop.ps1`, or `make stop`).

To watch both engines migrate a clean database from scratch, add `--clean`:

```bash
./start.sh --clean
```

### Requirements

Java 21+ and Maven 3.9+. That is all — H2 is embedded, there is nothing external to provision.
Docker is optional.

---

## The experiment

The design decision that makes an honest comparison possible: **each engine gets its own database.**

```
                     ┌─────────────────────────┐
                     │  Spring Boot (one JVM)  │
                     └────────────┬────────────┘
                    ┌─────────────┴─────────────┐
            FlywayConfig                  LiquibaseConfig
        migrates on startup            migrates on startup
                    │                             │
        ┌───────────▼───────────┐   ┌─────────────▼─────────────┐
        │   ./data/flywaydb     │   │   ./data/liquibasedb      │
        │  category             │   │  category                 │  ← identical
        │  product              │   │  product                  │     business
        │  product_audit        │   │  product_audit            │     schema
        │  v_product_catalog    │   │  v_product_catalog        │
        ├───────────────────────┤   ├───────────────────────────┤
        │ flyway_schema_history │   │ DATABASECHANGELOG         │  ← the only
        │                       │   │ DATABASECHANGELOGLOCK     │     difference
        └───────────────────────┘   └───────────────────────────┘
```

Both engines are wired **explicitly** in `@Configuration` classes rather than through Spring Boot
auto-configuration. Auto-configuration would hide exactly the thing this project exists to show: what
it actually takes to bootstrap each tool.

```java
// FlywayConfig — a DataSource, a location, and it runs. The rest is hardening.
@Bean(name = "flyway", initMethod = "migrate")
public Flyway flyway(@Qualifier(DATA_SOURCE) DataSource dataSource) {
    return Flyway.configure()
            .dataSource(dataSource)
            .locations(locations)
            .baselineOnMigrate(baselineOnMigrate)
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .load();
}

// LiquibaseConfig — more knobs, because changesets are a richer model than SQL files.
@Bean("liquibase")
public SpringLiquibase liquibase(@Qualifier(DATA_SOURCE) DataSource dataSource) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(changeLog);
    liquibase.setContexts(contexts);
    liquibase.setDefaultSchema(defaultSchema);
    return liquibase;
}
```

### The migrations, side by side

Each Flyway migration has a deliberate Liquibase counterpart, so the two directories can be read as a
translation table.

| # | Flyway (`db/migration/`) | Liquibase (`db/changelog/changes/`) | What it demonstrates |
| --- | --- | --- | --- |
| 1 | `V1__create_category_table.sql` | `001-create-category-table.xml` | Raw SQL vs database-agnostic XML tags |
| 2 | `V2__create_product_table.sql` | `002-create-product-table.yaml` | Liquibase's YAML format; the `<sql>` escape hatch for check constraints |
| 3 | `V3__seed_reference_data.sql` | `003-seed-reference-data.sql` | Liquibase's SQL-formatted changelog — same SQL, still tracked |
| 4 | `V4__add_product_audit_table.sql` | `004-add-product-audit-table.xml` | **Preconditions and explicit `<rollback>` — no Flyway Community equivalent** |
| 5 | `V5__add_product_active_flag.sql` | `005-add-product-active-flag.xml` | Contexts and labels; expand/backfill split into two changesets |
| R | `R__product_catalog_view.sql` | `006-product-catalog-view.xml` | Flyway's `R__` prefix vs Liquibase's `runOnChange="true"` |

That last column is why the applied counts differ: **6 vs 7**. Liquibase's `005` splits the column
addition and the data backfill into two independently rollback-able changesets, where Flyway's `V5`
does both in one script. That is a modelling difference, not a discrepancy.

---

## What the comparison found

Everything below is verified by the code or by tests, not asserted from memory.

**Both engines produce the same schema.** Three tables, one view, and every column matching by name
and type. `ComparisonService.diff()` compares tables, views and columns and returns an empty list.
Indexes are deliberately excluded from the equivalence check — H2 auto-generates constraint-backing
indexes under names that legitimately differ between engines, so including them would report noise as
drift.

**Flyway has a real embedded status API. Liquibase does not.** This shows up directly in the two
implementations of `MigrationHistoryProvider`:

```java
// FlywayHistoryService — the engine tells you its own state.
MigrationInfo[] all = flyway.info().all();

// LiquibaseHistoryService — no equivalent API, so the bookkeeping table is the interface.
SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE,
       MD5SUM, DESCRIPTION, COMMENTS, CONTEXTS, LABELS, DEPLOYMENT_ID
FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED
```

**They record different things.** Flyway persists per-migration execution time; Liquibase persists
none. Liquibase records author, contexts, labels and deployment id; Flyway records none of them —
attribution lives only in git. Neither is strictly richer, but the asymmetry is real and the API
returns `"executionTimeMs": null` for every Liquibase changeset to make it visible rather than hide it.

**Rollback is the sharpest divide.** `004-add-product-audit-table.xml` carries a `<rollback>` block
and a precondition that marks the changeset as run if `product` is missing. `V4__add_product_audit_table.sql`
can offer neither: reverting it means writing a new forward migration, because `undo` is a paid Flyway
Teams feature.

**Portability is not absolute.** `002-create-product-table.yaml` has to drop to an inline `<sql>` block
for check constraints, because Liquibase has no portable tag for them. The abstraction is good, not total.

The full 18-row judgement matrix lives in
[`FeatureMatrix.java`](src/main/java/com/wallaceespindola/dbmigration/service/FeatureMatrix.java) and is
served at `/api/v1/comparison/features`.

### Choosing between them

**Flyway** if your team is SQL-fluent, you target one database engine, and you value reviewable diffs
and a shallow learning curve. Version collisions on merge fail loudly, which is a feature.

**Liquibase** if you need to support multiple database vendors, want first-class rollback, or need
conditional execution per environment through contexts, labels and preconditions.

Both are good tools. Neither choice is one you will regret; the wrong choice is having no migration
tool at all.

---

## Execution flow

1. Spring Boot starts and, because `DataSourceAutoConfiguration` is excluded, builds both
   `DataSource` beans from `app.datasource.*` in `application.yml`.
2. `FlywayConfig` creates the `Flyway` bean with `initMethod = "migrate"` — migrations run during bean
   initialisation, against `./data/flywaydb`.
3. `LiquibaseConfig` creates the `SpringLiquibase` bean, which applies the master changelog during
   `afterPropertiesSet()`, against `./data/liquibasedb`.
4. Both `JdbcTemplate` beans declare `@DependsOn` on their engine, so nothing can read a database
   before it has been migrated.
5. Tomcat starts. The dashboard loads and calls `/api/v1/comparison`.
6. `ComparisonService` asks both `MigrationHistoryProvider`s for their status, asks
   `SchemaInspectionService` for both schemas, diffs them, and attaches the feature matrix.

Sequence diagrams for both the startup and the request path are in
[`docs/diagrams/04-sequence-diagrams.md`](docs/diagrams/04-sequence-diagrams.md).

---

## REST API

Every response is wrapped in an envelope carrying `success`, `message`, `data` and a server `timestamp`.

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/v1/comparison` | Full report: both statuses, both schemas, the diff, the feature matrix |
| `GET` | `/api/v1/comparison/features` | The 18-row feature matrix on its own |
| `GET` | `/api/v1/migrations` | Migration status for both engines |
| `GET` | `/api/v1/migrations/engines` | Engine metadata |
| `GET` | `/api/v1/migrations/{engine}` | Applied migrations for one engine |
| `GET` | `/api/v1/migrations/{engine}/schema` | Schema objects one engine produced |
| `GET` | `/api/v1/catalog/{engine}` | Seeded product data from one engine's database |
| `GET` | `/api/v1/health` | UP only when both migrated databases answer |

`{engine}` is `flyway` or `liquibase`, case-insensitive. An unknown engine returns `400` with a
message naming the valid options.

```bash
curl -s localhost:8080/api/v1/comparison | jq '.data.schemasEquivalent, .data.schemaDifferences'
curl -s localhost:8080/api/v1/migrations/liquibase | jq '.data.migrations[].identifier'
```

---

## Project layout

```
src/main/java/com/wallaceespindola/dbmigration/
├── config/       FlywayConfig, LiquibaseConfig, OpenApiConfig  — one DataSource + engine each
├── controller/   Migration, Comparison, Catalog, Health, GlobalExceptionHandler
├── domain/       MigrationEngine enum
├── dto/          Java records: ApiResponse, ComparisonReport, AppliedMigration, SchemaSnapshot, …
└── service/      MigrationHistoryProvider + 2 impls, SchemaInspectionService,
                  ComparisonService, FeatureMatrix

src/main/resources/
├── db/migration/     Flyway: V1–V5 plus R__ repeatable
├── db/changelog/     Liquibase: master YAML + XML/YAML/SQL changesets
└── static/           The dashboard: index.html, css/styles.css, js/app.js

docs/
├── articles/      DZone, Medium, LinkedIn, Dev.to, Substack
├── diagrams/      Class, Component, Deployment, Sequence, Class model, ERD — PlantUML + Mermaid
├── presentation/  Markdown deck, PPTX, Google Slides script, reproducible generator
└── specs/         PRD_Specs.md
```

Java **records** carry every DTO; **Lombok** (`@Slf4j`, `@RequiredArgsConstructor`) removes the
service and controller boilerplate.

---

## Development

```bash
make help          # list every target
make verify        # tests + JaCoCo coverage gate (fails under 80% line coverage)
make test          # tests only
make run           # foreground via the Spring Boot plugin
make clean-db      # delete both H2 databases so migrations re-run from scratch
make api           # smoke-test a running instance and print the headline result
```

Run a single test class or method:

```bash
mvn test -Dtest=ApplicationIntegrationTest
mvn test -Dtest='ApplicationIntegrationTest$CentralClaim'
mvn test -Dtest=ComparisonServiceTest#identicalSchemasHaveNoDifferences
```

**86 tests, 98.8% line coverage** (82.5% branch) against an 80% gate. The unit tests cover the diff
algorithm, engine resolution, the `DATABASECHANGELOG` row mapping, health degradation and error
handling. `ApplicationIntegrationTest` boots the whole application against two real freshly-migrated
in-memory databases and asserts on what the engines actually produced — including that both seeded
identical data and that Liquibase applied changesets written in XML, YAML *and* SQL. The coverage
report lands in `target/site/jacoco/index.html`.

CI runs `mvn verify` and then boots the packaged jar and asserts `schemasEquivalent` is still true, so
a migration change that makes the two engines diverge fails the build.

> **Note:** Flyway logs `H2 2.3.232 is newer than this version of Flyway and support has not been
> tested`. Both versions come from the Spring Boot 3.4.2 BOM and everything works; overriding either
> one to silence the warning would take the project off Spring Boot's tested dependency set.

---

## Documentation

| Where | What |
| --- | --- |
| [`docs/diagrams/`](docs/diagrams/) | Class, Component, Deployment, Sequence, Class model and ERD — each in PlantUML and Mermaid |
| [`docs/articles/`](docs/articles/) | Long-form write-ups for DZone, Medium, LinkedIn, Dev.to and Substack |
| [`docs/presentation/`](docs/presentation/) | 24-slide deck as Markdown and PPTX, plus a delivery script |
| [`docs/specs/PRD_Specs.md`](docs/specs/PRD_Specs.md) | The original requirements |

---

## Author

**Wallace Espindola**
Senior Software Engineer & Solution Architect — Java/Spring Boot, Python/FastAPI, cloud-native
microservices on Kubernetes and OpenShift.

- GitHub: <https://github.com/wallaceespindola/>
- LinkedIn: <https://www.linkedin.com/in/wallaceespindola/>
- Email: <wallace.espindola@gmail.com>

## License

Apache License 2.0 — see [LICENSE](LICENSE).
