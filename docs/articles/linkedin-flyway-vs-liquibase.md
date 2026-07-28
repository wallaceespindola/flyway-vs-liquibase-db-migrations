![Flyway vs Liquibase — the same schema built twice by two migration engines, 6 migrations against 7 changesets, with a zero-difference result](https://raw.githubusercontent.com/wallaceespindola/flyway-vs-liquibase-db-migrations/main/docs/images/banner-linkedin.png)

I ran Flyway and Liquibase against two identical schemas in the same app. One number surprised me. The other one didn't.

Most "Flyway vs Liquibase" posts are opinions dressed up as comparisons. I built a Spring Boot 3.4.2 app on Java 21 instead: two independent H2 databases, one migrated by Flyway, one by Liquibase, both targeting the identical logical schema, both wired through explicit `@Configuration` beans so nothing is hidden by autoconfiguration.

**The number that didn't surprise me:** `GET /api/v1/comparison` returns `schemasEquivalent: true` with zero structural differences. Same tables, same columns, same view, built by two tools that never talked to each other.

**The number that did:** Flyway applied 6 migrations. Liquibase applied 7. Same schema, different count. Here's the actual response:

```json
{
  "schemasEquivalent": true,
  "schemaDifferences": [],
  "flyway":    { "appliedCount": 6, "historyTable": "flyway_schema_history" },
  "liquibase": { "appliedCount": 7, "historyTable": "DATABASECHANGELOG" }
}
```

Here's why the count differs, straight from the actual migration files.

## Why 6 vs 7

Flyway's `V5` does a column addition and a data backfill in one script — one migration, two concerns bundled together. Liquibase splits the same logical change into two independently labeled changesets:

```xml
<!-- changes/005-add-product-active-flag.xml -->
<changeSet id="005-add-product-active-flag" author="wallaceespindola"
           labels="schema-evolution">
    <addColumn tableName="product">
        <column name="active" type="BOOLEAN" defaultValueBoolean="true"/>
    </addColumn>
</changeSet>

<changeSet id="005b-backfill-product-active-flag" author="wallaceespindola"
           labels="data-backfill">
    <update tableName="product">
        <column name="active" valueBoolean="false"/>
        <where>stock_quantity = 0</where>
    </update>
</changeSet>
```

`005` is labeled `schema-evolution`, `005b` is labeled `data-backfill`. A pipeline can filter and run them independently — ship the column everywhere, hold the backfill for a maintenance window. That's the entire reason the applied count differs. It's a modeling choice, not a discrepancy.

## The comparison that actually matters: rollback

Flyway's `V4` adds an audit table. Look at what's missing:

```sql
-- V4__add_product_audit_table.sql
-- Note what is NOT here: a rollback. Flyway Community has no undo — reverting means
-- writing a new forward migration (V6__drop_product_audit_table.sql).
CREATE TABLE product_audit
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT NOT NULL,
    audit_action VARCHAR(20) NOT NULL,
    CONSTRAINT fk_product_audit_product FOREIGN KEY (product_id) REFERENCES product (id)
);
```

The Liquibase changeset that builds the same table carries a precondition guard and a real rollback:

```xml
<!-- changes/004-add-product-audit-table.xml -->
<changeSet id="004-add-product-audit-table" author="wallaceespindola">
    <preConditions onFail="MARK_RAN" onFailMessage="product table missing">
        <tableExists tableName="product"/>
    </preConditions>
    <!-- createTable / createIndex / sql insert omitted, same shape as the SQL above -->
    <rollback>
        <dropIndex tableName="product_audit" indexName="idx_product_audit_product"/>
        <dropTable tableName="product_audit"/>
    </rollback>
</changeSet>
```

`liquibase rollbackCount 1` is a real, tested command against that changeset. Flyway's `undo` exists, but it's a paid Flyway Teams feature — Community users revert by writing a new forward migration, exactly like the comment in `V4` says.

## The decision, compressed

| Question | Flyway | Liquibase |
|---|---|---|
| Team writes SQL fluently, one DB engine | Fits well | Extra ceremony for no payoff |
| Need `rollbackCount` as a tested command, not a runbook | No — Community has none | Yes, built in |
| Need the same changelog to run against multiple DB dialects | No | Yes |
| Merge conflicts should fail loud, at validation time | Yes — version collision blocks the build | Quieter — an include-list conflict can merge clean and fail later |
| Need per-changeset author/context/label tracked in the DB itself | Not tracked | Tracked (`AUTHOR`, `CONTEXTS`, `LABELS` columns) |
| Need per-migration execution time recorded | Yes | No — `DATABASECHANGELOG` has no duration column |

Neither tool loses on schema correctness — both produced identical structures in this project. What you're actually picking is a rollback story, a merge-conflict failure mode, and a bookkeeping model.

The full repo — both migration trees, the live comparison endpoint, and an 18-row feature matrix generated from running code — is at [github.com/wallaceespindola/flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations).

Does your team treat rollback as a supported operation, or as "write a new migration and hope"? Let me know your thoughts in the comments.

---

Wallace Espindola, Senior Software Engineer & Solution Architect
GitHub: https://github.com/wallaceespindola/
LinkedIn: https://www.linkedin.com/in/wallaceespindola/

Need more tech insights?
Check out my GitHub, LinkedIn, and Speaker Deck.
Happy coding!
