---
title: "I Built the Same Database Schema Twice — With Flyway and With Liquibase — Then Diffed the Result"
subtitle: "A Spring Boot experiment that settles the schema question and surfaces the real differences: bookkeeping, rollback, and what each tool's API actually exposes"
tags: [software-engineering, spring-boot, java, databases, devops]
---

## The Question I Got Tired of Answering With Opinions

Every few months, someone on a team I'm working with asks whether we should be using Flyway or Liquibase for the next service. And every few months, the conversation turns into a recycled set of opinions: Flyway is simpler, Liquibase is more powerful, Flyway is SQL so it's "safer," Liquibase has rollback so it's "safer." Everyone's right and nobody's checked.

So I stopped answering with opinions and built a Spring Boot 3.4.2 application on Java 21 that does the only honest thing: create the exact same schema twice, once with each tool, against two separate H2 databases, then query both `INFORMATION_SCHEMA`s and diff them. Not a blog-post diff. An actual structural comparison, exposed over a REST endpoint, that either says the schemas match or tells you exactly where they don't.

The code is at [github.com/wallaceespindola/flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations). Here's what I found.

## Setting Up an Honest Comparison

The trap in most "Flyway vs Liquibase" writeups is that they compare a Flyway toy example against a Liquibase toy example that don't actually build the same thing. I wanted both sides to produce an identical business schema: a `category` table, a `product` table with a foreign key back to category, a `product_audit` trail table, and a `v_product_catalog` view joining the two.

Each engine gets its own H2 database file — `./data/flywaydb` for Flyway, `./data/liquibasedb` for Liquibase — configured explicitly rather than through Spring Boot's `spring.flyway.*` or `spring.liquibase.*` auto-configuration:

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

I wired both by hand on purpose. Auto-configuration hides exactly the details I wanted visible — what each tool actually needs to bootstrap, and how each one decides its migrations have run before the rest of the application starts reading from the database.

## The Result Nobody Argues About: The Schema Matches

Here's the part that should end the "which tool builds a better schema" debate for good. The app exposes `GET /api/v1/comparison`, which reads both databases through `INFORMATION_SCHEMA`, compares tables, views and columns, and reports the outcome:

```json
{
  "data": {
    "schemasEquivalent": true,
    "schemaDifferences": [],
    "flywayStatus": { "engine": "FLYWAY", "appliedCount": 6, "pendingCount": 0 },
    "liquibaseStatus": { "engine": "LIQUIBASE", "appliedCount": 7, "pendingCount": 0 }
  },
  "message": "Both engines produced an equivalent business schema"
}
```

Zero differences. Both tools converge on the same tables, the same columns, the same view. The applied-migration count differs — six on the Flyway side, seven on the Liquibase side — but that's not drift, it's a modeling choice: Liquibase's changeset 005 got split into `005-add-product-active-flag` and `005b-backfill-product-active-flag` so the schema change and the data backfill can be labeled and filtered independently. One logical change, expressed as one Flyway migration or two Liquibase changesets. Both are correct.

I'll say the quiet part out loud: if your evaluation criteria is "which tool produces the right database," you can stop reading here and pick either one. That was never really the question. The real differences live in what happens around the schema — the bookkeeping, the rollback story, and what you get if you try to read a tool's status from inside your own application instead of from its CLI.

## Where the Tools Actually Diverge: Discovery

Flyway finds its migrations by scanning a classpath location for files named `V<version>__description.sql`, and orders them by version number. No manifest, no list to maintain — the filesystem naming convention is the whole discovery mechanism:

```
V1__create_category_table.sql
V2__create_product_table.sql
V3__seed_reference_data.sql
V4__add_product_audit_table.sql
V5__add_product_active_flag.sql
R__product_catalog_view.sql
```

Liquibase requires an explicit master changelog that lists every changeset file in order:

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

This sounds like a minor mechanical detail until you think about what happens when two developers on two branches both add a migration at the same time. On the Flyway side, if they both pick `V6__`, the build fails or Flyway's validation catches the duplicate version — loud, obvious, impossible to miss in code review. On the Liquibase side, both branches add a line to the `include:` list, usually at the end of the file. A clean auto-merge can silently produce a changelog where one branch's include line vanishes or lands in the wrong order, and nothing breaks until someone notices a changeset never ran against a database. Same category of problem — a merge conflict in migration ordering — with a completely different failure signature. One is a compile-time argument. The other is a production mystery three weeks later.

## Format Freedom: Plain SQL vs Four Formats in One Changelog

Flyway Community migrations are exactly what they look like — SQL, in the dialect of your target database, nothing translated:

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

Liquibase supports XML, YAML, JSON and SQL as equally first-class formats, mixable inside a single changelog. I used three of them deliberately in this project to make the point concrete rather than theoretical. The same category table, in Liquibase XML:

```xml
<!-- changes/001-create-category-table.xml -->
<changeSet id="001-create-category-table" author="wallaceespindola" context="demo">
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
</changeSet>
```

The product table went in as YAML instead (`002-create-product-table.yaml`), and the seed data dropped to Liquibase-formatted SQL because check constraints — `CHECK (price >= 0)` — have no portable Liquibase tag and have to be written as raw SQL anyway. That mixing is the point: Liquibase doesn't force you to be abstract everywhere, only where it can be.

What you're trading is real in both directions, and it's not a "Liquibase wins" story. Flyway's SQL is something every engineer on your team can read cold, no changeset grammar to learn — that matters more than it sounds like in a code review with someone who's never touched either tool before. Liquibase's abstraction buys you dialect portability: the exact same `createTable` changeset can, in principle, generate correct DDL against PostgreSQL, MySQL or Oracle without being rewritten. If your product only ever runs against one database, that portability is a feature you're paying learning-curve cost for and never cashing in.

## The Part That Actually Changes How You Operate: Rollback

This is the centerpiece, because it's the difference that shows up during an incident, not during development.

Flyway's V4 migration adds a `product_audit` table. There's no rollback attached to it — there's no mechanism for one in Flyway Community:

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
```

The equivalent Liquibase changeset builds the identical table, but wraps it in a precondition and ships with a tested rollback path:

```xml
<!-- changes/004-add-product-audit-table.xml -->
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

    <rollback>
        <dropIndex tableName="product_audit" indexName="idx_product_audit_product"/>
        <dropTable tableName="product_audit"/>
    </rollback>
</changeSet>
```

`<preConditions onFail="MARK_RAN">` means this changeset checks that `product` exists first, and if it doesn't, marks itself as applied without exploding the deploy. Flyway has no declarative version of that check — you'd have to write it into the SQL itself, or fail. And `<rollback>` means `liquibase rollbackCount 1` is a real, testable command against this exact changeset. Flyway's own `undo` exists, but it's gated behind Flyway Teams, the paid tier. In Flyway Community, "rolling back" a bad migration means writing and shipping a new forward migration that undoes it — which is fine as a discipline, but it's a different discipline than "run the documented rollback," and it means your rollback path is untested until the day you actually need it.

## Reading History Without Learning SQL: The API Asymmetry Nobody Mentions

This is the finding that surprised me most, because it only shows up once you try to embed either tool's status inside your own application rather than shelling out to a CLI.

Flyway gives you a proper, in-process, read-only status API. Call `flyway.info()` and you get every known migration back as objects, applied and pending, with state, checksum and execution time already populated:

```java
// FlywayHistoryService.java
MigrationInfo[] all = flyway.info().all();

List<AppliedMigration> applied = Arrays.stream(all)
        .filter(info -> info.getInstalledOn() != null)
        .map(FlywayHistoryService::toAppliedMigration)
        .sorted(Comparator.comparing(AppliedMigration::appliedAt))
        .toList();
```

Liquibase has nothing comparable. There's no lightweight call that hands you "the changesets that have run" as objects. The honest way to build a status endpoint for Liquibase is to query its own bookkeeping table directly:

```java
// LiquibaseHistoryService.java
private static final String HISTORY_QUERY =
        """
        SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE,
               MD5SUM, DESCRIPTION, COMMENTS, CONTEXTS, LABELS, DEPLOYMENT_ID
        FROM DATABASECHANGELOG
        ORDER BY ORDEREXECUTED
        """;
```

That's genuinely how `LiquibaseHistoryService` in this project works — there's no shortcut available. In exchange for that inconvenience, `DATABASECHANGELOG` records more than Flyway's `flyway_schema_history` ever does: author, contexts, labels, deployment ID, execution type. Flyway records none of that anywhere in the database — if you want to know who wrote a migration, you're looking at git blame, not the schema.

There's one more asymmetry buried in the code that's easy to miss: Flyway persists execution time in milliseconds for every migration it runs. Liquibase's `DATABASECHANGELOG` table has no duration column at all, so `LiquibaseHistoryService` literally returns `null` for that field on every row — not a bug, just a gap in what Liquibase tracks. If you want per-changeset timing data for a performance postmortem, Liquibase isn't going to hand it to you.

And identity itself differs. Flyway identifies a migration by version — one global, ordered namespace, `V1`, `V2`, `V3`. Liquibase identifies a changeset by the composite of id, author and filename. When I built the shared `AppliedMigration` record both services populate, that difference is exactly why it needed two separate construction paths rather than one — the identity models aren't just cosmetically different, they're structurally different.

## The One Place They Agree: Repeatable Objects

Views that should redefine themselves rather than accumulate version history are the one spot where both tools land on the same mechanism, just triggered differently. Flyway uses a filename prefix:

```sql
-- R__product_catalog_view.sql
CREATE OR REPLACE VIEW v_product_catalog AS
SELECT p.id AS product_id, p.sku AS sku, p.name AS product_name, p.price AS price,
       p.stock_quantity AS stock_quantity, p.active AS active,
       c.id AS category_id, c.name AS category_name
FROM product p
JOIN category c ON c.id = p.category_id;
```

Liquibase uses an attribute on an ordinary changeset:

```xml
<!-- changes/006-product-catalog-view.xml -->
<changeSet id="006-product-catalog-view" author="wallaceespindola" context="demo"
           runOnChange="true">
    <createView viewName="v_product_catalog" replaceIfExists="true">
        SELECT p.id AS product_id, p.sku AS sku, p.name AS product_name, p.price AS price,
               p.stock_quantity AS stock_quantity, p.active AS active,
               c.id AS category_id, c.name AS category_name
        FROM product p
        JOIN category c ON c.id = p.category_id
    </createView>
</changeSet>
```

Both re-execute automatically when their checksum changes and stay quiet otherwise. Same idea, filed under a filename in one tool and an attribute in the other.

## So Which One Do You Actually Pick

If your team writes and reviews SQL comfortably, targets one database engine, and you're fine treating "revert" as "ship a new forward migration," Flyway's simplicity is a real advantage, not a marketing line — and its embedded `info()` API means you can build a status dashboard without ever touching a bookkeeping table.

If you need portability across database engines, if rollback needs to be a tested command rather than a runbook written under pressure, or if you need per-changeset conditional execution finer than "which folder am I pointing at," Liquibase's extra ceremony buys real operational capability — preconditions, contexts, labels, and a rollback path that's part of the changeset itself.

Both tools built the identical schema in this project. That was never in question. What's actually different is what happens when something goes wrong, and how much of the "what happened and who did it" story you get for free versus have to reconstruct from git.

The full source — REST API, both migration trees, Swagger UI, and the comparison endpoint that produced the JSON above — is at [github.com/wallaceespindola/flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations). Clone it, run `mvn spring-boot:run`, and hit `/api/v1/comparison` yourself — don't take my word for the diff.

---

**Wallace Espindola** is a Senior Software Engineer and Solution Architect.
GitHub: [github.com/wallaceespindola](https://github.com/wallaceespindola/) · LinkedIn: [linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)

Need more tech insights?
Check out my GitHub, LinkedIn, and Speaker Deck.
Happy coding!
