---
title: Flyway vs Liquibase - I Ran Both Against the Same Schema So You Don't Have To
published: false
description: A Spring Boot 3.4 app that migrates two identical H2 databases with Flyway and Liquibase, then diffs the resulting schemas at runtime. Clone it, run it, see the numbers yourself.
tags: java, database, springboot, devops
cover_image: https://raw.githubusercontent.com/wallaceespindola/flyway-vs-liquibase-db-migrations/main/docs/images/banner-devto.png
---

# Flyway vs Liquibase: I Ran Both Against the Same Schema So You Don't Have To

![Flyway vs Liquibase — the same schema built twice by two migration engines, 6 migrations against 7 changesets, with a zero-difference result](https://raw.githubusercontent.com/wallaceespindola/flyway-vs-liquibase-db-migrations/main/docs/images/banner-devto.png)

Most "Flyway vs Liquibase" articles are opinion pieces. This one comes with a repo you can clone and a running app that proves its own claims — an actual side-by-side comparison instead of a blog post asking you to trust it.

## What I built

A Spring Boot 3.4.2 app on Java 21 that stands up **two independent H2 file databases** with an identical logical schema. One gets migrated by Flyway, the other by Liquibase, and both are wired explicitly through their own `@Configuration` class instead of relying on Spring Boot's auto-configuration to hide what each tool does at startup.

```java
// FlywayConfig.java — three method calls, and Flyway is done
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
```

```java
// LiquibaseConfig.java — needs more knobs, buys more behavior
@Bean("liquibase")
public SpringLiquibase liquibase(@Qualifier(DATA_SOURCE) DataSource dataSource) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(changeLog);
    liquibase.setContexts(contexts);
    liquibase.setDefaultSchema(defaultSchema);
    liquibase.setDropFirst(false);
    liquibase.setShouldRun(true);
    return liquibase;
}
```

That contrast in bean size is the whole Flyway pitch in one glance: fewer knobs, less ceremony. Liquibase's extra setup buys you contexts, a configurable default schema, and — as you'll see below — rollback and richer audit data.

## Clone it and run it yourself

```bash
git clone https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations.git
cd flyway-vs-liquibase-db-migrations
mvn spring-boot:run
```

The app boots on port 8080, migrates both H2 databases on startup (`./data/flywaydb` and `./data/liquibasedb`), and exposes a REST API plus Swagger UI at `http://localhost:8080/swagger-ui.html`. There's also an H2 console at `/h2-console` if you want to poke at either database directly.

Hit the headline endpoint:

```bash
curl -s http://localhost:8080/api/v1/comparison | jq
```

## What Flyway actually migrated

Five versioned SQL scripts plus one repeatable migration, six migrations total:

```
V1__create_category_table.sql
V2__create_product_table.sql
V3__seed_reference_data.sql
V4__add_product_audit_table.sql
V5__add_product_active_flag.sql
R__product_catalog_view.sql
```

Flyway migrations are plain SQL in the target database's dialect — no abstraction layer. `V1` looks exactly like what you'd hand-write against H2:

```sql
-- V1__create_category_table.sql
CREATE TABLE category
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_category_name UNIQUE (name)
);

CREATE INDEX idx_category_name ON category (name);
```

The `R__` prefix is Flyway's repeatable migration convention — this script re-runs automatically whenever its checksum changes, which is how the app keeps `v_product_catalog` in sync without bumping a version number every time the view definition changes.

## What Liquibase actually migrated

Liquibase needs an explicit master changelog, because unlike Flyway it doesn't discover files by naming convention — it composes the changelog from an include list you write yourself:

```yaml
# db.changelog-master.yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-category-table.xml
  - include:
      file: db/changelog/changes/002-create-product-table.yaml
  - include:
      file: db/changelog/changes/003-seed-reference-data.sql
  - include:
      file: db/changelog/changes/004-add-product-audit-table.xml
  - include:
      file: db/changelog/changes/005-add-product-active-flag.xml
  - include:
      file: db/changelog/changes/006-product-catalog-view.xml
```

001 through 006 apply seven changesets in total, because 005 actually contains two: `005-add-product-active-flag` and `005b-backfill-product-active-flag`. That file mixes XML, YAML and raw SQL across the six included files, on purpose, to prove all three formats are first-class citizens in the same changelog.

Changeset 004 is the one with no Flyway equivalent at all — a precondition that guards the change, plus an explicit rollback block:

```xml
<changeSet id="004-add-product-audit-table" author="wallaceespindola" context="demo">
    <preConditions onFail="MARK_RAN" onFailMessage="product table missing, skipping audit table">
        <tableExists tableName="product"/>
    </preConditions>

    <createTable tableName="product_audit">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" primaryKeyName="pk_product_audit" nullable="false"/>
        </column>
        <column name="product_id" type="BIGINT">
            <constraints nullable="false" foreignKeyName="fk_product_audit_product"
                         references="product(id)"/>
        </column>
        <!-- audit_action, changed_by, changed_at columns omitted for brevity -->
    </createTable>

    <rollback>
        <dropIndex tableName="product_audit" indexName="idx_product_audit_product"/>
        <dropTable tableName="product_audit"/>
    </rollback>
</changeSet>
```

Compare that to Flyway's `V4__add_product_audit_table.sql`, which creates the same table with no rollback at all — the comment in that file says it outright: reverting it means writing `V6__drop_product_audit_table.sql` by hand.

## The comparison endpoint

`GET /api/v1/comparison` runs both status reports, snapshots both schemas from `INFORMATION_SCHEMA`, diffs tables/views/columns, and attaches the full feature matrix. Every response is wrapped in the same envelope:

```json
{
  "success": true,
  "message": "Both engines produced an equivalent business schema",
  "data": {
    "flyway": {
      "engine": "FLYWAY",
      "engineDisplayName": "Flyway",
      "historyTable": "flyway_schema_history",
      "appliedCount": 6,
      "pendingCount": 0,
      "upToDate": true,
      "migrations": [
        {
          "identifier": "V1",
          "description": "create category table",
          "author": "n/a",
          "checksum": "-1250524602",
          "executionTimeMs": 2,
          "status": "Success"
        }
      ]
    },
    "liquibase": {
      "engine": "LIQUIBASE",
      "engineDisplayName": "Liquibase",
      "historyTable": "DATABASECHANGELOG",
      "appliedCount": 7,
      "pendingCount": 0,
      "upToDate": true,
      "migrations": [
        {
          "identifier": "001-create-category-table::wallaceespindola",
          "description": "Create the category reference table",
          "author": "wallaceespindola",
          "checksum": "8:3f...e2",
          "executionTimeMs": null,
          "status": "EXECUTED"
        }
      ]
    },
    "schemasEquivalent": true,
    "schemaDifferences": [],
    "featureMatrix": [
      {
        "feature": "Rollback / undo",
        "flyway": "Not in Community — reverting means writing a new forward migration. undo is a Teams feature",
        "liquibase": "Built in: auto-inferred for most changes, or declared with <rollback>",
        "edge": "LIQUIBASE"
      }
    ]
  },
  "timestamp": "2026-07-27T10:15:32.401Z"
}
```

(Trimmed for length — the real response has all 6 Flyway migrations, all 7 Liquibase changesets, and all 18 feature matrix rows.)

Two fields prove the sharpest technical findings by their presence, not by prose: Flyway's `executionTimeMs` is `8`, Liquibase's is `null` — Liquibase genuinely does not persist per-changeset duration. And Flyway's `author` is hardcoded to `"n/a"` because Flyway Community never asks who wrote a migration, while Liquibase's `author` attribute is mandatory on every changeset.

`schemasEquivalent: true` and an empty `schemaDifferences` array is the actual headline: both tools, run independently, produced the identical business schema — same tables, same columns, same view. The 18-row `featureMatrix` is what actually separates them, and you can hit it standalone at `GET /api/v1/comparison/features` if you don't need the schema data.

## Other endpoints worth poking at

```bash
# Status for one engine, including its full migration history
curl -s http://localhost:8080/api/v1/migrations/flyway | jq
curl -s http://localhost:8080/api/v1/migrations/liquibase | jq

# The schema each engine actually produced, read from INFORMATION_SCHEMA
curl -s http://localhost:8080/api/v1/migrations/flyway/schema | jq

# Business data proving the migrations produced a working, queryable schema
curl -s http://localhost:8080/api/v1/catalog/liquibase | jq
```

`/api/v1/catalog/{engine}` reads through `v_product_catalog` — the view created by `R__product_catalog_view.sql` on the Flyway side and by the `runOnChange="true"` changeset 006 on the Liquibase side. Same query, same result set, two different mechanisms for "redefine this view whenever it changes":

```xml
<!-- changes/006-product-catalog-view.xml -->
<changeSet id="006-product-catalog-view" author="wallaceespindola" context="demo"
           runOnChange="true">
    <createView viewName="v_product_catalog" replaceIfExists="true">
        SELECT p.id AS product_id, p.sku AS sku, p.name AS product_name,
               p.price AS price, p.stock_quantity AS stock_quantity, p.active AS active,
               c.id AS category_id, c.name AS category_name
        FROM product p
        JOIN category c ON c.id = p.category_id
    </createView>
    <rollback>
        <dropView viewName="v_product_catalog"/>
    </rollback>
</changeSet>
```

## Reading history: the one place the tools genuinely diverge in code

Flyway's `FlywayHistoryService` doesn't touch SQL at all — it calls `flyway.info()` and gets structured `MigrationInfo` objects back, applied and pending, already ordered:

```java
MigrationInfo[] all = flyway.info().all();
List<AppliedMigration> applied = Arrays.stream(all)
        .filter(info -> info.getInstalledOn() != null)
        .map(FlywayHistoryService::toAppliedMigration)
        .sorted(Comparator.comparing(AppliedMigration::appliedAt))
        .toList();
```

`LiquibaseHistoryService` has no such API to call. It queries `DATABASECHANGELOG` directly:

```java
private static final String HISTORY_QUERY = """
    SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE,
           MD5SUM, DESCRIPTION, COMMENTS, CONTEXTS, LABELS, DEPLOYMENT_ID
    FROM DATABASECHANGELOG
    ORDER BY ORDEREXECUTED
    """;
```

That's not a workaround I invented for this project — it's the standard way to read Liquibase status when you're embedding it in an application rather than shelling out to the CLI. Liquibase simply doesn't ship a lightweight, read-only status API the way Flyway does. In exchange, the table you're forced to query directly carries a lot more columns: author, contexts, labels, deployment id — all things Flyway's bookkeeping table doesn't record at all.

## Trade-offs worth knowing before you pick one

Flyway's version numbers make merge conflicts loud. Two branches both writing `V6__something.sql` fails validation immediately and obviously. Liquibase's failure mode is quieter: two engineers each adding a line to the master changelog's include list can merge cleanly in git and only break — wrong order, duplicate changeset — when someone actually runs it.

Flyway Community genuinely has no rollback. If you need `undo`, that's a paid Teams feature. Liquibase's rollback is open source and works out of the box, either auto-inferred or explicitly declared, as changeset 004 above shows.

Neither tool does drift detection in the version this project uses on the Flyway side — Liquibase's `diff` and `diffChangeLog` commands compare two databases and generate the delta; Flyway Community has nothing comparable.

## Wrapping up

The repo has the full 18-row feature matrix served live at `/api/v1/comparison/features`, plus every migration file and changelog referenced above: https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations

Clone it, run `mvn spring-boot:run`, and check the JSON yourself instead of taking my word for any of this.

What's your approach? Drop it in the comments.

Need more tech insights?
Check out my GitHub, LinkedIn, and Speaker Deck.
Happy coding!

Wallace Espindola
GitHub: https://github.com/wallaceespindola/
LinkedIn: https://www.linkedin.com/in/wallaceespindola/
