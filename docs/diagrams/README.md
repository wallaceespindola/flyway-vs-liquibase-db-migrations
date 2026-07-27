# Architecture Diagrams

Diagrams for `flyway-vs-liquibase-db-migrations`. Every diagram was derived by reading the source, not from a
template: class and method names, endpoint paths, bean names, SQL identifiers and column types all match the code
in `src/main/`.

Each diagram exists twice: a PlantUML `.puml` file and a Markdown file with a Mermaid block. Content is
equivalent; pick whichever your tooling renders.

## Index

| # | Diagram | Files | What it shows |
|---|---|---|---|
| 1 | Class diagram | [`01-class-diagram.puml`](01-class-diagram.puml) · [`01-class-diagram.md`](01-class-diagram.md) | Java types across `config`, `domain`, `service`, `controller`, `dto`, with `MigrationHistoryProvider` and its two implementations |
| 2 | Component diagram | [`02-component-diagram.puml`](02-component-diagram.puml) · [`02-component-diagram.md`](02-component-diagram.md) | C4-style container view: browser to REST controllers to services to two `JdbcTemplate`s to two H2 databases, with Flyway and SpringLiquibase as migration components |
| 3 | Deployment diagram | [`03-deployment-diagram.puml`](03-deployment-diagram.puml) · [`03-deployment-diagram.md`](03-deployment-diagram.md) | One JVM, embedded Tomcat on 8080, two H2 files under `./data`, optional container packaging |
| 4 | Sequence diagrams | [`04-sequence-diagrams.puml`](04-sequence-diagrams.puml) · [`04-sequence-diagrams.md`](04-sequence-diagrams.md) | Startup migration of both databases during context refresh, the `GET /api/v1/comparison` request flow, and the failure paths handled by `GlobalExceptionHandler` |
| 5 | Class model diagram | [`05-class-model-diagram.puml`](05-class-model-diagram.puml) · [`05-class-model-diagram.md`](05-class-model-diagram.md) | The record model: `ApiResponse`, `ComparisonReport`, `MigrationStatusReport`, `AppliedMigration`, `SchemaSnapshot`, `ProductView`, `FeatureComparison`, `MigrationEngine` |
| 6 | ERD | [`06-erd.puml`](06-erd.puml) · [`06-erd.md`](06-erd.md) | `category` to `product` to `product_audit`, the `v_product_catalog` view, both engines' bookkeeping tables, and the script-to-changeset mapping |

## Rendering

**Mermaid (`.md`)** — renders natively on GitHub, GitLab, Notion and most wikis. No tooling required; open the
file in the web UI. For local preview use the Mermaid Live Editor (<https://mermaid.live>) or a Markdown preview
extension with Mermaid support.

**PlantUML (`.puml`)** — requires a renderer:

```bash
# CLI, with plantuml installed via brew/apt
plantuml docs/diagrams/*.puml            # writes .png next to each source
plantuml -tsvg docs/diagrams/*.puml      # SVG output

# Docker, no local install
docker run --rm -v "$PWD:/data" plantuml/plantuml -tsvg /data/docs/diagrams
```

IntelliJ IDEA and VS Code both have PlantUML plugins that render on save.

`04-sequence-diagrams.puml` and `06-erd.puml` each contain two `@startuml ... @enduml` blocks; the CLI emits one
image per block, suffixed with the block name.

## Accuracy notes

Two elements are drawn dashed or greyed because they are referenced in the project description but do not exist in
the repository as of this commit:

- **Static dashboard** — `src/main/resources/static` is absent. Swagger UI at `/swagger-ui.html` is the working
  browser client. The diagrams keep the dashboard node so the intended topology is visible, marked as planned.
- **Docker packaging** — no `Dockerfile` or `docker-compose.yml` exists. The container node in the deployment
  diagram is optional and marked as such. If added, `./data` must be a mounted volume or both databases are
  discarded on every container restart.

Everything else is verifiable against the source:

- 6 Flyway migrations (`V1`..`V5`, `R__product_catalog_view`) versus 7 Liquibase changesets
  (`001`, `002`, `003`, `004`, `005`, `005b`, `006`) — the difference is `005b`, a separate backfill changeset.
- `DataSourceAutoConfiguration` is excluded in `application.yml`; both `DataSource` beans are declared in
  `FlywayConfig` and `LiquibaseConfig`.
- `FlywayHistoryService` reads history through `flyway.info()`; `LiquibaseHistoryService` reads it with a
  `SELECT` against `DATABASECHANGELOG`.
- `FeatureMatrix` holds exactly 18 `FeatureComparison` rows.

## Regenerating after code changes

These are hand-written, not generated. When you change a controller path, a bean name, a record component or a
migration script, update the corresponding `.puml` and `.md` in the same commit.

---

Author: Wallace Espindola — [github.com/wallaceespindola](https://github.com/wallaceespindola/) ·
[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/) · wallace.espindola@gmail.com
