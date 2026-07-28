**Suggested subject line:** I put Flyway and Liquibase in the same app and made them prove it

---

*Preview text: Not another opinion piece. I cloned my own repo, ran both migration engines against identical H2 databases, and pasted the actual files and the actual JSON below. Copy-paste along if you want.*

---

![Flyway vs Liquibase — the same schema built twice by two migration engines, 6 migrations against 7 changesets, with a zero-difference result](https://raw.githubusercontent.com/wallaceespindola/flyway-vs-liquibase-db-migrations/main/docs/images/banner-substack.png)

I've picked Flyway on some projects and Liquibase on others, and the reasoning was never as rigorous as I'd like to admit. Whatever the team already knew, whatever the last architect wired up, whatever felt faster to bootstrap on a Friday afternoon. So I built a small Spring Boot 3.4.2 app on Java 21 that runs both engines against two separate H2 databases with the identical logical schema, and I'm going to walk you through the actual files instead of describing them.

## The bootstrap, side by side

Both engines are wired through explicit `@Configuration` beans — no `spring.flyway.*` or `spring.liquibase.*` autoconfiguration hiding what each tool needs to start:

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

Flyway needs a `DataSource` and a location string, full stop. Liquibase needs those plus a changelog entry point, contexts and a default schema. That's not Liquibase being clumsy — those extra fields are what buys you rollback, preconditions, and conditional execution later in this email.

## Run it yourself in 60 seconds

No opinion required, just clone it:

```bash
git clone https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations.git
cd flyway-vs-liquibase-db-migrations
./start.sh
```

That boots Spring Boot on port 8080, migrates `./data/flywaydb` with Flyway and `./data/liquibasedb` with Liquibase, and serves a dashboard at `http://localhost:8080/`. Stop it with `./stop.sh` when you're done (`.\start.ps1` / `.\stop.ps1` on Windows, or `make start` / `make stop` if you'd rather use the Makefile).

Now hit the endpoint that matters:

```bash
curl -s http://localhost:8080/api/v1/comparison | jq '.data | {schemasEquivalent, schemaDifferences, flyway: .flyway.appliedCount, liquibase: .liquibase.appliedCount}'
```

```json
{
  "schemasEquivalent": true,
  "schemaDifferences": [],
  "flyway": 6,
  "liquibase": 7
}
```

Two independent engines, two independent databases that never talk to each other, zero structural differences. `ComparisonService.diff()` compares tables, views and columns to get that empty array — it deliberately skips indexes, because H2 auto-generates constraint-backing indexes under names that legitimately differ between the two engines, and counting that as "drift" would just be reporting noise.

The interesting number is the 6 vs 7. Same business schema, different migration counts. Here's why.

## The comparison that actually matters: V4 vs changeset 004

Flyway's fourth migration adds an audit trail table. There's no rollback anywhere in the file:

```sql
-- V4__add_product_audit_table.sql
-- Note what is NOT here: a rollback. Flyway Community has no undo — reverting means writing a new
-- forward migration (V6__drop_product_audit_table.sql). Compare with the Liquibase equivalent,
-- changes/004-add-product-audit-table.xml, which carries an explicit <rollback> block.
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

The Liquibase changeset that produces the exact same table carries a `<preConditions>` guard and a real `<rollback>` block:

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
        <!-- audit_action, changed_by, changed_at columns follow the same shape as the SQL above -->
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

Two capabilities on the Liquibase side have no Flyway Community equivalent. `<preConditions onFail="MARK_RAN">` checks that `product` exists before running, and marks itself applied instead of failing the deploy if it doesn't. And `<rollback>` turns `liquibase rollbackCount 1` into a command you can actually run and test, not a manual script you write under pressure at 2am. Flyway's `undo` exists, but it's locked behind Flyway Teams — a paid tier. Community users revert by writing a new forward migration, which is exactly what the comment in `V4` tells you to do.

## Where the applied-count gap comes from

`V5__add_product_active_flag.sql` does the column addition and the backfill in one script:

```sql
-- V5__add_product_active_flag.sql
ALTER TABLE product
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE product
SET active = FALSE
WHERE stock_quantity = 0;

CREATE INDEX idx_product_active ON product (active);
```

Liquibase splits the identical logical change into two changesets, each independently labeled:

```xml
<!-- changes/005-add-product-active-flag.xml -->
<changeSet id="005-add-product-active-flag" author="wallaceespindola" context="demo"
           labels="schema-evolution">
    <addColumn tableName="product">
        <column name="active" type="BOOLEAN" defaultValueBoolean="true">
            <constraints nullable="false"/>
        </column>
    </addColumn>
    <createIndex tableName="product" indexName="idx_product_active">
        <column name="active"/>
    </createIndex>
    <rollback>
        <dropIndex tableName="product" indexName="idx_product_active"/>
        <dropColumn tableName="product" columnName="active"/>
    </rollback>
</changeSet>

<changeSet id="005b-backfill-product-active-flag" author="wallaceespindola" context="demo"
           labels="data-backfill">
    <update tableName="product">
        <column name="active" valueBoolean="false"/>
        <where>stock_quantity = 0</where>
    </update>
    <rollback>
        <update tableName="product">
            <column name="active" valueBoolean="true"/>
            <where>stock_quantity = 0</where>
        </update>
    </rollback>
</changeSet>
```

That's the whole story behind 6 vs 7. `005` is tagged `labels="schema-evolution"` and `005b` is tagged `labels="data-backfill"`, so a pipeline can filter and run them independently — apply the schema change everywhere, hold the backfill for a maintenance window if you want to. Flyway's `V5` bundles both into one atomic script. Neither approach is wrong; Liquibase's split is just more granular, and that granularity is the entire reason the applied-migration count differs between the two engines even though the resulting schema is identical.

## Reading the history back: info() vs a raw SELECT

Flyway ships a status API you call in-process, no SQL required:

```java
// FlywayHistoryService.java
MigrationInfo[] all = flyway.info().all();
List<AppliedMigration> applied = Arrays.stream(all)
        .filter(info -> info.getInstalledOn() != null)
        .map(FlywayHistoryService::toAppliedMigration)
        .sorted(Comparator.comparing(AppliedMigration::appliedAt))
        .toList();
```

Liquibase has no equivalent read API, so `LiquibaseHistoryService` queries its bookkeeping table directly:

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

Flyway's `MigrationInfo` also carries `executionTime`, so the app's JSON reports a real millisecond value for every Flyway migration. Query `DATABASECHANGELOG` for the same field and there's nothing there — Liquibase genuinely doesn't persist per-changeset duration, so `LiquibaseHistoryService` returns `null` for `executionTimeMs` on every row. That's not a bug in this project; it's what the table actually contains. In exchange, that same table gives you `AUTHOR`, `CONTEXTS`, `LABELS` and `DEPLOYMENT_ID` — none of which exist anywhere in Flyway's `flyway_schema_history`.

## What I'd pick, and when

If your team writes SQL fluently, targets one database engine, and you're fine with "revert" meaning "write and test a new forward migration" — pick Flyway. The bootstrap is three lines, code review means reading SQL everyone already knows, and a version collision between two branches fails loud at validation time instead of merging quietly.

If you need `rollbackCount` as a real, tested operation instead of a runbook you improvise at 2am, need the same changelog to run against more than one database dialect, or need contexts and labels to filter what runs per environment — Liquibase earns the extra setup. The changeset id + author composite key, the `<preConditions>` tag, the `runOnChange="true"` view changeset — none of that is ceremony for its own sake. It's the tool doing something Flyway Community structurally can't.

Both landed on the same schema in this project. What you're actually choosing is a rollback story and a bookkeeping model, not a "better" tool. Clone the repo, run `./start.sh`, and check the JSON for yourself instead of taking my word for any of it: [github.com/wallaceespindola/flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations)

---

Wallace Espindola
GitHub: https://github.com/wallaceespindola/ · LinkedIn: https://www.linkedin.com/in/wallaceespindola/

Need more tech insights?
Check out my GitHub, LinkedIn, and Speaker Deck.
Happy coding!
