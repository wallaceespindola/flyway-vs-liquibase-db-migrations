---
title: "Flyway vs Liquibase: What Actually Differs When You Build the Same Schema Twice"
tags: [java, spring-boot, database, flyway, liquibase, devops, sql]
estimated-read-time: "12 minutes"
audience: "Enterprise Java / Spring Boot developers evaluating a migration tool"
---

![Flyway vs Liquibase — the same schema built twice by two migration engines, 6 migrations against 7 changesets, with a zero-difference result](https://raw.githubusercontent.com/wallaceespindola/flyway-vs-liquibase-db-migrations/main/docs/images/banner-dzone.png)
## The Problem With Most Flyway vs Liquibase Comparisons

Most comparisons of Flyway and Liquibase read like feature checklists lifted from each vendor's own marketing page. You get a table with checkmarks, a paragraph about "convention over configuration," another about "database-agnostic changesets," and a conclusion that dodges the actual question: if you run both tools against the same schema, do you end up in the same place?

I wanted a real answer, so I built one. [flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations) is a Spring Boot 3.4.2 application on Java 21 that stands up two independent H2 databases side by side. One is migrated entirely by Flyway, the other entirely by Liquibase. Both target the identical logical schema: a `category` table, a `product` table, a `product_audit` table, and a `v_product_catalog` view. The app then exposes a REST API that reads both databases' `INFORMATION_SCHEMA`, diffs them structurally, and reports whether the two engines actually converged.

They do. `GET /api/v1/comparison` returns `schemasEquivalent: true` with zero structural differences. That result is the backbone of this article — not because it's surprising, but because it lets us stop arguing about whether the tools "basically do the same thing" and focus on where they genuinely don't: bookkeeping, rollback, portability and the API surface you get when you embed either tool in an application.

## Why This Matters Beyond the Checklist

If you're picking a migration tool for a new service, the schema outcome was never really in doubt — both tools are mature enough to create tables, indexes, foreign keys and views correctly. What decides the outcome for your team is:

- What happens when a migration needs to be reverted in production
- How much of the change history is queryable without parsing changelog files
- How merge conflicts in migration history show up in code review
- Whether you can embed the tool's status without writing SQL against its internal tables
- What you're paying for if you eventually need drift detection or rollback beyond a single environment

Those are the questions this project's code actually answers, because the answers are visible in `FlywayHistoryService`, `LiquibaseHistoryService`, and the migration files themselves — not in a vendor comparison page.

## Project Setup: Two Engines, Wired Explicitly

The project deliberately avoids Spring Boot's `spring.flyway.*` and `spring.liquibase.*` auto-configuration. Instead, `FlywayConfig` and `LiquibaseConfig` wire each engine by hand against its own `DataSource`, so you can see exactly what each tool needs to bootstrap:

```java
// FlywayConfig.java
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
// LiquibaseConfig.java
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

Both are three configuration properties away from running: a `DataSource`, a location, and a couple of flags. Flyway needs a directory to scan. Liquibase needs a changelog entry point plus contexts and a default schema. That extra property on the Liquibase side isn't an accident — it's the first hint of the bigger difference between the two models, which shows up as soon as you look at how each tool finds its migrations.

Both databases live in `./data/flywaydb` and `./data/liquibasedb` as separate H2 files (see `application.yml`), so there's no shared state to fudge the comparison.

## Discovery Model: Convention vs Explicit Manifest

Flyway scans `classpath:db/migration`, finds every `V<version>__description.sql` file, sorts by version, and runs whatever hasn't been applied yet. There's no manifest — the filesystem *is* the manifest:

```
V1__create_category_table.sql
V2__create_product_table.sql
V3__seed_reference_data.sql
V4__add_product_audit_table.sql
V5__add_product_active_flag.sql
R__product_catalog_view.sql
```

Liquibase requires an explicit root changelog that lists every file it should include, in order:

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

This is where the tools' philosophies genuinely diverge, and it cascades into everything else. Flyway's ordering lives in a filename; Liquibase's lives in a file you can read top to bottom. Flyway's model means two developers on different branches can both create `V6__` and collide loudly at merge time — the version number itself is the conflict, and it's impossible to miss. Liquibase's model means the conflict lands in the `include` list of the master changelog. If two branches each add a new `include` line at the end of the file, an automatic merge can produce a changelog that silently drops or reorders one of them, and nothing fails until someone notices a changeset never ran. Neither failure mode is fatal, but one fails loud and the other fails quiet, and "fails quiet" is the one you want to know about before you pick a tool for a team of more than two people.

## Format Freedom: One Language vs Four

Flyway Community migrations are plain SQL. That's the entire value proposition in one sentence — no abstraction, no tags to learn, what you write is what runs:

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

Liquibase supports XML, YAML, JSON and SQL as first-class, mixable formats within the same changelog. This repo uses three of them on purpose to prove the point. The category table above, expressed in Liquibase XML:

```xml
<!-- changes/001-create-category-table.xml -->
<changeSet id="001-create-category-table" author="wallaceespindola" context="demo">
    <comment>Create the category reference table</comment>
    <createTable tableName="category">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" primaryKeyName="pk_category" nullable="false"/>
        </column>
        <column name="name" type="VARCHAR(100)">
            <constraints nullable="false" unique="true" uniqueConstraintName="uk_category_name"/>
        </column>
        <column name="description" type="VARCHAR(500)"/>
        <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <createIndex tableName="category" indexName="idx_category_name">
        <column name="name"/>
    </createIndex>
    <rollback>
        <dropIndex tableName="category" indexName="idx_category_name"/>
        <dropTable tableName="category"/>
    </rollback>
</changeSet>
```

The product table (`002-create-product-table.yaml`) is written in YAML instead. That file is also where the abstraction runs out: check constraints have no portable Liquibase tag, so it drops to an inline `<sql>` block for them. The seed data (`003-seed-reference-data.sql`) uses the SQL-formatted changelog for a different reason — to show that raw SQL is a first-class changelog format, still tracked and still rollback-able:

```sql
--liquibase formatted sql
--changeset wallaceespindola:003-seed-reference-data context:demo splitStatements:true
--comment: Seed category and product reference data
INSERT INTO category (name, description)
VALUES ('Databases', 'Relational and NoSQL database products');
...
--rollback DELETE FROM product;
--rollback DELETE FROM category;
```

The trade-off is real in both directions. Flyway's SQL is something every reviewer on your team can already read without learning anything new — that's a genuine ergonomics win in code review. Liquibase's abstract tags buy you a changeset that can, in theory, run unmodified against PostgreSQL, MySQL or Oracle, because `createTable`/`column`/`type: BIGINT` gets translated to the target dialect at runtime instead of being written in it. If you're shipping the same product against more than one database engine — which happens more often than teams expect, usually because of an acquisition or a client with existing infrastructure — that portability stops being theoretical.

## The Centerpiece: Rollback and Preconditions

This is the comparison that actually matters for production operations. Flyway V4 adds an audit table with no way to undo it short of writing a new forward migration:

```sql
-- V4__add_product_audit_table.sql
-- Note what is NOT here: a rollback. Flyway Community has no undo — reverting means
-- writing a new forward migration (V6__drop_product_audit_table.sql).
CREATE TABLE product_audit
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    audit_action VARCHAR(20)  NOT NULL,
    changed_by   VARCHAR(100) NOT NULL DEFAULT 'system',
    changed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_audit_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE INDEX idx_product_audit_product ON product_audit (product_id);

INSERT INTO product_audit (product_id, audit_action, changed_by)
SELECT id, 'CREATED', 'flyway-migration'
FROM product;
```

The Liquibase changeset that produces the identical table carries a `<preConditions>` guard and an explicit `<rollback>` block:

```xml
<!-- changes/004-add-product-audit-table.xml -->
<changeSet id="004-add-product-audit-table" author="wallaceespindola" context="demo">
    <preConditions onFail="MARK_RAN" onFailMessage="product table missing, skipping audit table">
        <tableExists tableName="product"/>
    </preConditions>

    <comment>Add the product audit trail, guarded by a precondition</comment>

    <createTable tableName="product_audit">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" primaryKeyName="pk_product_audit" nullable="false"/>
        </column>
        <column name="product_id" type="BIGINT">
            <constraints nullable="false" foreignKeyName="fk_product_audit_product"
                         references="product(id)"/>
        </column>
        <column name="audit_action" type="VARCHAR(20)">
            <constraints nullable="false"/>
        </column>
        <column name="changed_by" type="VARCHAR(100)" defaultValue="system">
            <constraints nullable="false"/>
        </column>
        <column name="changed_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
            <constraints nullable="false"/>
        </column>
    </createTable>

    <createIndex tableName="product_audit" indexName="idx_product_audit_product">
        <column name="product_id"/>
    </createIndex>

    <sql>
        INSERT INTO product_audit (product_id, audit_action, changed_by)
        SELECT id, 'CREATED', 'liquibase-changeset' FROM product
    </sql>

    <rollback>
        <dropIndex tableName="product_audit" indexName="idx_product_audit_product"/>
        <dropTable tableName="product_audit"/>
    </rollback>
</changeSet>
```

Two capabilities are on display here that have no Flyway Community equivalent. `<preConditions onFail="MARK_RAN">` lets the changeset check that `product` exists before it runs, and marks itself as applied instead of failing the deploy if it doesn't — useful for changelogs that need to survive being run against environments that aren't perfectly in sync. And `<rollback>` makes `liquibase rollbackCount 1` an operation you can actually run and test, not a manual SQL script you write under pressure at 2 AM. Flyway's `undo` command exists, but it's a Flyway Teams (paid) feature — Community users revert by writing a new forward migration, full stop.

Contexts and labels extend the same idea to environment-conditional execution. Changeset `005-add-product-active-flag.xml` is tagged `labels="schema-evolution"` and its sibling `005b-backfill-product-active-flag` is tagged `labels="data-backfill"` — split so the two concerns can be filtered independently in a pipeline, something the app runs with `contexts=demo` (see `application.yml`, `app.migration.liquibase.contexts`). Flyway's nearest equivalent — placeholder substitution or per-environment migration locations — operates at the filesystem or variable level, not per individual change.

## Repeatable Objects: Same Idea, Different Address

Views that should be redefined rather than versioned are the one place the two tools land on an identical mechanism, just declared in different places. Flyway uses an `R__` filename prefix:

```sql
-- R__product_catalog_view.sql
CREATE OR REPLACE VIEW v_product_catalog AS
SELECT p.id AS product_id, p.sku AS sku, p.name AS product_name, p.price AS price,
       p.stock_quantity AS stock_quantity, p.active AS active,
       c.id AS category_id, c.name AS category_name
FROM product p
JOIN category c ON c.id = p.category_id;
```

Liquibase uses a `runOnChange="true"` attribute on an ordinary changeset:

```xml
<!-- changes/006-product-catalog-view.xml -->
<changeSet id="006-product-catalog-view" author="wallaceespindola" context="demo"
           runOnChange="true">
    <comment>Product catalog view, redefined whenever this changeset changes</comment>
    <createView viewName="v_product_catalog" replaceIfExists="true">
        SELECT p.id AS product_id, p.sku AS sku, p.name AS product_name, p.price AS price,
               p.stock_quantity AS stock_quantity, p.active AS active,
               c.id AS category_id, c.name AS category_name
        FROM product p
        JOIN category c ON c.id = p.category_id
    </createView>
    <rollback>
        <dropView viewName="v_product_catalog"/>
    </rollback>
</changeSet>
```

Both re-run automatically when their checksum changes and skip re-execution otherwise. This is the one row in the feature matrix marked a tie, and it earns it.

## Reading Back the History: Where the Asymmetry Shows Up in Code

This is the part that doesn't show up in marketing comparisons because it only becomes visible when you actually embed both tools in an application and try to expose their status over an API.

Flyway ships a first-class, in-process status API. `Flyway.info()` returns every known migration — applied and pending — as `MigrationInfo` objects with state, checksum and execution time already attached:

```java
// FlywayHistoryService.java
@Override
public MigrationStatusReport status() {
    MigrationInfo[] all = flyway.info().all();

    List<AppliedMigration> applied = Arrays.stream(all)
            .filter(info -> info.getInstalledOn() != null)
            .map(FlywayHistoryService::toAppliedMigration)
            .sorted(Comparator.comparing(AppliedMigration::appliedAt))
            .toList();

    int pending = (int) Arrays.stream(all).filter(info -> info.getInstalledOn() == null).count();
    ...
}

private static AppliedMigration toAppliedMigration(MigrationInfo info) {
    String identifier = info.getVersion() != null
            ? "V" + info.getVersion()
            : "R__" + info.getDescription();

    return new AppliedMigration(
            identifier,
            info.getDescription(),
            info.getScript(),
            info.getType().name(),
            NO_AUTHOR, // Flyway does not record who authored a migration
            Objects.toString(info.getChecksum(), null),
            info.getInstalledOn().toInstant(),
            info.getExecutionTime() < 0 ? null : (long) info.getExecutionTime(),
            info.getState().getDisplayName());
}
```

No SQL. No table name to remember. Just an object graph.

Liquibase has no equivalent lightweight read API. `LiquibaseHistoryService` has to query `DATABASECHANGELOG` directly:

```java
// LiquibaseHistoryService.java
private static final String HISTORY_QUERY =
        """
        SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE,
               MD5SUM, DESCRIPTION, COMMENTS, CONTEXTS, LABELS, DEPLOYMENT_ID
        FROM DATABASECHANGELOG
        ORDER BY ORDEREXECUTED
        """;

private static final RowMapper<AppliedMigration> CHANGELOG_ROW_MAPPER =
        (ResultSet rs, int rowNum) -> {
            Timestamp executedAt = rs.getTimestamp("DATEEXECUTED");
            return new AppliedMigration(
                    "%s::%s".formatted(rs.getString("ID"), rs.getString("AUTHOR")),
                    describe(rs),
                    rs.getString("FILENAME"),
                    rs.getString("EXECTYPE"),
                    rs.getString("AUTHOR"),
                    rs.getString("MD5SUM"),
                    executedAt == null ? null : executedAt.toInstant(),
                    null, // Liquibase does not record per-changeset execution time
                    rs.getString("EXECTYPE"));
        };
```

That's not a design flaw in this project — it's an honest reflection of what's available. Liquibase's public Java API doesn't expose a read-only, already-applied-changesets accessor comparable to `flyway.info()`; the bookkeeping table is the interface. In exchange for that inconvenience, the table records considerably more: author, contexts, labels, deployment ID and execution type, none of which Flyway tracks anywhere in the database. Flyway's identifier is a single version number in one ordered namespace; Liquibase's identifier is a composite of `id + author + filename`, which is exactly why the shared `AppliedMigration` DTO in this project needs two different construction paths to normalize them into one shape.

One more asymmetry worth calling out explicitly because it's easy to miss: Flyway persists `execution_time` in milliseconds for every migration. Liquibase's `DATABASECHANGELOG` table has no duration column at all — `LiquibaseHistoryService` returns `null` for `executionTimeMs` on every row, not because of a bug, but because the data genuinely doesn't exist. If you need per-changeset timing for a performance investigation on the Liquibase side, you're instrumenting it yourself.

## The Measured Result

Here's what `GET /api/v1/comparison` on this project actually returns after both engines finish migrating their own database:

```json
{
  "data": {
    "schemasEquivalent": true,
    "schemaDifferences": [],
    "flyway":    { "engine": "FLYWAY",    "appliedCount": 6, "pendingCount": 0, "upToDate": true },
    "liquibase": { "engine": "LIQUIBASE", "appliedCount": 7, "pendingCount": 0, "upToDate": true }
  },
  "message": "Both engines produced an equivalent business schema"
}
```

Six Flyway migrations, seven Liquibase changesets (005 is split into 005 and 005b), zero structural differences in tables, views or columns once the bookkeeping tables — `flyway_schema_history`, `DATABASECHANGELOG`, `DATABASECHANGELOGLOCK` — are excluded from the comparison. That exclusion is deliberate and documented in `SchemaInspectionService`: those tables exist for the tool's own use, not as part of the business schema, so counting them as a "difference" would be reporting noise as drift.

The applied-migration count difference (6 vs 7) isn't a discrepancy — it's the same logical change (add the active flag, backfill out-of-stock rows) expressed as one atomic Flyway migration versus two separately labeled Liquibase changesets. Both approaches are valid; Liquibase's split just makes the two concerns independently filterable by label in a pipeline.

## Decision Guide

Pick **Flyway** when:

- Your team is comfortable writing and reviewing raw SQL, and you want migrations that read exactly like what runs against the database
- You're targeting one database engine for the foreseeable future
- You want a lightweight, in-process status API you can build a health check or dashboard on without touching a bookkeeping table
- Your release process treats "revert" as "write and test a new forward migration," and you're fine with that discipline
- You want merge conflicts in migration history to fail loudly at the filename level rather than silently in a manifest

Pick **Liquibase** when:

- You need to ship the same schema against more than one database engine, or you don't yet know which engine production will use
- Rollback needs to be a supported, tested command — not a runbook someone writes under pressure
- You need conditional execution per environment (contexts, labels, preconditions) finer-grained than "which folder do I point at"
- Author, context and deployment attribution need to live in the database itself, not just in git blame
- You're already invested in Liquibase Pro for drift detection, policy checks, or flow orchestration, and want the open-source core to match

Neither tool is "wrong" for a use case the other is "right" for — both converged on an identical schema in this project, which is the entire point of building it this way. What you're actually choosing is a bookkeeping model, a rollback story and a merge-conflict profile. Pick the one that matches how your team already works, not the one with more checkmarks on a slide.

The full source, including the REST API (`/api/v1/comparison`, `/api/v1/migrations/{engine}`, `/api/v1/catalog/{engine}`), Swagger UI, and both migration trees, is at [github.com/wallaceespindola/flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations).

---

**Author**: Wallace Espindola, Senior Software Engineer & Solution Architect
GitHub: [github.com/wallaceespindola](https://github.com/wallaceespindola/) · LinkedIn: [linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
Repo: [github.com/wallaceespindola/flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations)

Need more tech insights?
Check out my GitHub, LinkedIn, and Speaker Deck.
Happy coding!
