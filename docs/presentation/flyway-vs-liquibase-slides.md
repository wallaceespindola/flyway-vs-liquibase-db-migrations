# Flyway vs Liquibase

## A measured comparison, not an opinion

**Wallace Espindola** — Senior Software Engineer & Solution Architect

- GitHub: [github.com/wallaceespindola](https://github.com/wallaceespindola)
- LinkedIn: [linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
- Code: [github.com/wallaceespindola/flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations)

Spring Boot 3.4.2 · Java 21 · Maven · H2 · plain JDBC

<!--
SPEAKER NOTES
This talk is backed by a running application, not a blog post. One Spring Boot process opens two
independent H2 databases. Flyway migrates one, Liquibase migrates the other, and the app then reads
both back from INFORMATION_SCHEMA and diffs them at runtime.
Set the frame early: I am not selling either tool. Both work. The interesting question is which
trade-offs you are buying, and this deck answers that with code from the repo and one measured
result at the end.
-->

---

## The problem: schema drift and manual DDL

- Application code is versioned, reviewed and rolled forward. The schema often is not.
- "Run this script on prod" in a chat message is not a deployment process.
- Environments diverge silently: dev has the column, staging does not, prod has it with a different type.
- Nobody can answer "which changes has this database actually seen?"
- Rebuilding an environment from scratch becomes archaeology.

<!--
SPEAKER NOTES
Everybody in the room has lived this. The symptom is not a dramatic outage, it is a slow accumulation
of uncertainty. You stop trusting that staging looks like production, so you stop testing against
staging, so defects reach production.
The root cause is that DDL is treated as an operational act rather than as source code. Two things
are missing: an ordered, reviewable record of every change, and a machine that applies exactly that
record to every environment.
-->

---

## Why migration tooling

A migration tool gives you four properties that scripts in a wiki do not:

- **Ordering** — changes apply in a defined sequence, once, everywhere.
- **Bookkeeping** — a table inside the database records what has been applied.
- **Integrity** — a checksum detects edits to already-applied changes.
- **Automation** — migration runs at application startup or in the pipeline, not by hand.

Flyway and Liquibase both deliver all four. Everything else in this talk is about how.

<!--
SPEAKER NOTES
Frame these four as the baseline, not the differentiator. Any tool that fails one of these is not a
candidate.
The checksum point deserves a beat: both tools hash applied changes, so editing a migration that has
already run fails validation instead of silently drifting. That single behaviour prevents a large
class of "it works on my machine" incidents.
The automation point matters for Spring Boot: both engines run before JPA initialises, so the schema
is guaranteed correct before the first entity is touched.
-->

---

## How Flyway works

- Drop SQL files into a location: `V1__create_category_table.sql`.
- The filename *is* the configuration: `V` = versioned, `1` = order, `__` separator, rest = description.
- `R__` prefix marks a repeatable migration: re-applied whenever its checksum changes.
- Flyway scans the location, sorts by version, runs what is not yet in `flyway_schema_history`.
- Scripts are raw SQL in the dialect of the target database. No abstraction layer.

```java
Flyway.configure()
      .dataSource(dataSource)
      .locations("classpath:db/migration")
      .baselineOnMigrate(true)
      .validateOnMigrate(true)
      .load();
```

<!--
SPEAKER NOTES
This is the whole model. There is no changelog file, no registry, no include list. Convention over
configuration in the most literal sense: discovery is a directory scan and an ordering rule.
Point at the bootstrap code — it is from config/FlywayConfig.java in the repo, unedited. Five builder
calls and Flyway is live. That brevity is Flyway's core value proposition and it is genuinely hard to
argue with.
validateOnMigrate(true) is the checksum enforcement. cleanDisabled(true) in the repo blocks the
destructive clean command, which you always want switched off outside a laptop.
-->

---

## How Liquibase works

- A **changeset** is the unit of change: `id` + `author` + source file identify it.
- Changesets live in XML, YAML, JSON or SQL — all first-class, all mixable.
- A **master changelog** explicitly lists every included file. Ordering is declared, not inferred.
- Changesets are database-agnostic; Liquibase emits the dialect at runtime.
- `preConditions`, `context`, `labels`, `rollback` and `runOnChange` are per-changeset attributes.

```java
SpringLiquibase liquibase = new SpringLiquibase();
liquibase.setDataSource(dataSource);
liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
liquibase.setContexts("demo");
liquibase.setDefaultSchema("PUBLIC");
```

<!--
SPEAKER NOTES
Liquibase needs more knobs than Flyway, and that is not an accident: it models changes abstractly, so
it needs to be told what to include, in what context, and against which schema.
The abstraction is the product. When you write <createTable> instead of CREATE TABLE, Liquibase
decides what H2, PostgreSQL or Oracle should actually receive. That is what unlocks portability, and
it is also what makes the diff harder to read for a reviewer who only knows SQL.
This bootstrap is from config/LiquibaseConfig.java, again unedited. SpringLiquibase implements
InitializingBean, so the changelog is applied during bean initialisation — the same lifecycle
position as Flyway's migrate().
-->

---

## The experiment design

Two independent H2 databases. One identical logical schema. One process.

| | Flyway side | Liquibase side |
|---|---|---|
| Database | `./data/flywaydb` | `./data/liquibasedb` |
| Wiring | `FlywayConfig` `@Configuration` | `LiquibaseConfig` `@Configuration` |
| Runner | `Flyway.migrate()` on bean init | `SpringLiquibase` on bean init |
| Reader | `flywayJdbcTemplate` (`@DependsOn`) | `liquibaseJdbcTemplate` (`@DependsOn`) |

The app then reads both schemas from `INFORMATION_SCHEMA` and diffs them.

<!--
SPEAKER NOTES
The design matters because it removes the usual hand-waving. Both engines are explicitly wired, with
Spring Boot's DataSourceAutoConfiguration excluded, so nothing is hidden behind auto-configuration
magic. You see the real bootstrap of each tool.
Each JdbcTemplate carries @DependsOn on its migration bean. That is what guarantees no read can
happen before the migrations have run, so the comparison always reflects a fully migrated database.
Then SchemaInspectionService queries INFORMATION_SCHEMA on both sides — it never trusts the migration
scripts, it reads what the database actually contains.
-->

---

## Flyway migrations: the walkthrough

Six migrations under `src/main/resources/db/migration`:

| File | What it does |
|---|---|
| `V1__create_category_table.sql` | `category` table + unique name + index |
| `V2__create_product_table.sql` | `product` + FK, 2 check constraints, 2 indexes |
| `V3__seed_reference_data.sql` | 3 categories, 5 products |
| `V4__add_product_audit_table.sql` | `product_audit` + FK + backfill from `product` |
| `V5__add_product_active_flag.sql` | `ADD COLUMN active` + backfill + index |
| `R__product_catalog_view.sql` | `v_product_catalog`, repeatable |

**6 migrations applied.**

<!--
SPEAKER NOTES
Walk the table left to right. Note that V3 seeds data through a versioned migration — the seed
becomes part of schema history and applies exactly once per environment, in order. That is a
deliberate choice and it is the right one for reference data.
V5 is the classic expand step of an expand/contract rollout: add a nullable-safe column with a
default, backfill it, index it. All in one migration because it is one logical change.
The R__ file has no version number. Flyway re-runs it whenever the file's checksum changes, which is
exactly what you want for views and stored procedures — objects you redefine rather than version.
-->

---

## Flyway code: expand/contract and repeatable

```sql
-- V5__add_product_active_flag.sql
ALTER TABLE product
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE product SET active = FALSE WHERE stock_quantity = 0;

CREATE INDEX idx_product_active ON product (active);
```

```sql
-- R__product_catalog_view.sql  (repeatable: re-runs when the checksum changes)
CREATE OR REPLACE VIEW v_product_catalog AS
SELECT p.id AS product_id, p.sku, p.name AS product_name,
       p.price, p.stock_quantity, p.active,
       c.id AS category_id, c.name AS category_name
FROM product p JOIN category c ON c.id = p.category_id;
```

<!--
SPEAKER NOTES
This is the ergonomic argument for Flyway in one slide. Anyone in the room who reads SQL can review
these two files with no additional vocabulary. There is nothing between the intent and the statement.
The cost is written on the same slide: this is H2 dialect. Move to PostgreSQL and BOOLEAN NOT NULL
DEFAULT TRUE is fine, but AUTO_INCREMENT in V1 is not. You would rewrite the files.
Note also what is missing from V5: any way to undo it. Flyway Community has no undo. Reverting means
writing V6__drop_product_active_flag.sql. We come back to this on the rollback slide.
-->

---

## Liquibase changelog: the walkthrough

Master changelog includes six files, in three formats:

| File | Format | What it demonstrates |
|---|---|---|
| `001-create-category-table.xml` | XML | portable `createTable` + explicit `rollback` |
| `002-create-product-table.yaml` | YAML | same semantics, terser diff; raw SQL for check constraints |
| `003-seed-reference-data.sql` | SQL | `--liquibase formatted sql`, `--rollback` directives |
| `004-add-product-audit-table.xml` | XML | `preConditions` + `rollback` |
| `005-add-product-active-flag.xml` | XML | `context` + `labels`, two changesets |
| `006-product-catalog-view.xml` | XML | `runOnChange="true"` |

**7 changesets applied** (005 contains two: the column and the backfill).

<!--
SPEAKER NOTES
The format mixing is not showing off — it is how real Liquibase projects look. Structural changes go
in XML or YAML for portability, and you drop to raw SQL only where a vendor feature has no portable
tag. 002 does exactly that: check constraints have no Liquibase tag, so there is an inline <sql>
block for them.
Count carefully: six files, seven changesets, because 005 splits the schema change from the data
backfill. That split is itself the point — they carry different labels, schema-evolution and
data-backfill, so they can be selected independently at runtime.
Compare with the Flyway side: six migrations there, seven changesets here, same resulting schema.
-->

---

## Liquibase code: preconditions and rollback

```xml
<changeSet id="004-add-product-audit-table" author="wallaceespindola" context="demo">
  <preConditions onFail="MARK_RAN"
                 onFailMessage="product table missing, skipping audit table">
    <tableExists tableName="product"/>
  </preConditions>

  <createTable tableName="product_audit"> ... </createTable>
  <createIndex tableName="product_audit" indexName="idx_product_audit_product"> ... </createIndex>

  <rollback>
    <dropIndex tableName="product_audit" indexName="idx_product_audit_product"/>
    <dropTable tableName="product_audit"/>
  </rollback>
</changeSet>
```

This changeset has no Flyway Community equivalent.

<!--
SPEAKER NOTES
Two capabilities are on display and neither exists in Flyway Community.
First, preConditions. If the product table is missing, the changeset is marked as run instead of
exploding halfway through a deploy. onFail has several modes — MARK_RAN, CONTINUE, HALT, WARN — so
you choose the failure semantics per changeset. Flyway has no declarative equivalent; you would write
defensive SQL or a Java migration.
Second, rollback. This block makes "liquibase rollbackCount 1" a supported, testable operation.
Liquibase can infer rollbacks for most structural changes, but declaring them explicitly keeps intent
visible and survives future refactoring of the forward change.
-->

---

## Side by side: the same table, two ways

**Flyway** — `V1__create_category_table.sql`

```sql
CREATE TABLE category (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_category_name UNIQUE (name));
```

**Liquibase** — `001-create-category-table.xml`

```xml
<createTable tableName="category">
  <column name="id" type="BIGINT" autoIncrement="true">
    <constraints primaryKey="true" primaryKeyName="pk_category" nullable="false"/></column>
  <column name="name" type="VARCHAR(100)">
    <constraints nullable="false" unique="true" uniqueConstraintName="uk_category_name"/></column>
</createTable>
```

<!--
SPEAKER NOTES
Same table, two philosophies, and the trade-off is visible in the character count.
The Flyway version is shorter and every reviewer already speaks it. The Liquibase version is longer
and requires knowing what the tags emit — but it names its constraints explicitly and it is not
bound to H2. AUTO_INCREMENT in the SQL version is H2 and MySQL syntax; PostgreSQL wants GENERATED
ALWAYS AS IDENTITY. Liquibase's autoIncrement="true" produces the right thing on either.
This is the whole comparison in miniature: directness versus abstraction. Neither is free.
-->

---

## Bookkeeping: what each engine records

| | `flyway_schema_history` | `DATABASECHANGELOG` |
|---|---|---|
| Identity | `version` (single ordered namespace) | `id` + `author` + `filename` |
| Author | not recorded | mandatory attribute |
| Checksum | CRC32 | MD5 |
| Execution time | **yes, milliseconds** | **not recorded** |
| Contexts / labels | not applicable | recorded per changeset |
| Deployment id | not recorded | recorded |
| Locking | database-level lock during run | `DATABASECHANGELOGLOCK` table |

<!--
SPEAKER NOTES
This slide is measured, not editorial — it is what the two tables actually contain in the running
demo. Two rows deserve emphasis in opposite directions.
Flyway records execution time per migration in milliseconds. Liquibase does not persist a
per-changeset duration at all. If you want to know which migration is the one making your deploys
slow, Flyway tells you for free.
Liquibase records author, contexts, labels and deployment id. Flyway's attribution lives only in git
history. If your auditors want to see who changed the schema without leaving the database, Liquibase
answers that and Flyway does not.
Also note Liquibase needs a second table for locking, Flyway uses a database-level lock.
-->

---

## Reading the history: two different APIs

**Flyway** — an embedded status API, no SQL needed:

```java
MigrationInfo[] all = flyway.info().all();
// applied + pending, ordered, with state, checksum and executionTime attached
```

**Liquibase** — query the bookkeeping table yourself:

```sql
SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE,
       MD5SUM, DESCRIPTION, COMMENTS, CONTEXTS, LABELS, DEPLOYMENT_ID
FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED
```

Liquibase has no lightweight read-only status API. The table is the public interface.

<!--
SPEAKER NOTES
This is the finding that surprised me most when building the demo, and it is a real operational
difference if you embed the engine in an application.
flyway.info() hands you objects. Applied and pending, already ordered, with state and checksum. Zero
SQL. That is FlywayHistoryService in the repo — the entire class is a stream over that array.
On the Liquibase side, LiquibaseHistoryService is a JdbcTemplate query against DATABASECHANGELOG,
because there is no equivalent. It works, and the table gives you more columns than Flyway's does,
but you are now coupled to Liquibase's internal table shape.
Also worth saying honestly: Liquibase only records what it has already run. Discovering pending
changesets requires a full changelog parse.
-->

---

## The measured result

`GET /api/v1/comparison` on the running application returns:

```json
"schemasEquivalent": true,
"schemaDifferences": []
```

- Tables on both sides: `category`, `product`, `product_audit`
- View on both sides: `v_product_catalog`
- Columns compared as `TABLE.COLUMN:TYPE` — **zero differences**
- The only difference is bookkeeping:
  `flyway_schema_history` vs `DATABASECHANGELOG` + `DATABASECHANGELOGLOCK`

<!--
SPEAKER NOTES
This is the headline. Six Flyway migrations and seven Liquibase changesets converge on byte-identical
business schemas. Tables, views and columns with their data types all match.
Be precise about what is compared and what is not. ComparisonService diffs tables, views and columns.
It deliberately excludes indexes, because H2 auto-generates constraint-backing indexes under
generated names that legitimately differ between the two engines — including them would report noise
as drift. That exclusion is documented in the code, not hidden.
The conclusion to draw: the choice between these tools is not about what schema you end up with. It
is entirely about the process around getting there.
-->

---

## Feature matrix (1 of 3) — authoring

| Capability | Flyway | Liquibase | Edge |
|---|---|---|---|
| Change format | Plain SQL only (Community); Java migrations for programmatic cases | XML, YAML, JSON or SQL, mixable in one changelog | Liquibase |
| Learning curve | One naming convention: `V1__name.sql` | Changeset model, changelog composition, tag vocabulary | Flyway |
| Database portability | None — scripts are in the target dialect | Changesets are abstract; dialect generated at runtime | Liquibase |
| Migration discovery | Convention: scan a location, order by version | Explicit master changelog listing every file | Flyway |
| Repeatable changes | `R__` scripts, re-run on checksum change | `runOnChange="true"` on any changeset | Tie |
| Review ergonomics | Diffs are SQL — every reviewer reads it | Diffs are XML/YAML tags — reviewers must know what they emit | Flyway |

<!--
SPEAKER NOTES
This matrix is served live at /api/v1/comparison/features and lives in FeatureMatrix.java. Eighteen
rows total, split across three slides. The "Edge" column is my judgement, stated as such — everything
to its left is factual.
Notice the pattern already forming: Flyway wins the rows about humans reading and writing changes,
Liquibase wins the rows about machines executing them in varied environments.
Do not linger on every row. Read two or three and move on; the deck is a reference the audience can
re-read.
-->

---

## Feature matrix (2 of 3) — execution

| Capability | Flyway | Liquibase | Edge |
|---|---|---|---|
| Rollback / undo | Not in Community — write a forward migration; `undo` is Teams | Built in: inferred, or declared with `<rollback>` | Liquibase |
| Conditional execution | Placeholders and per-environment locations; coarse | Preconditions, contexts and labels per changeset | Liquibase |
| Drift detection / diff | Not available in Community | `diff` and `diffChangeLog` generate the delta | Liquibase |
| Concurrency safety | Database-level lock for the migration run | Dedicated `DATABASECHANGELOGLOCK` table | Tie |
| Spring Boot integration | Auto-configured from `spring.flyway.*`, before JPA | Auto-configured from `spring.liquibase.*`, same position | Tie |
| Execution timing recorded | Yes, per migration, in milliseconds | No per-changeset duration persisted | Flyway |

<!--
SPEAKER NOTES
This is where Liquibase collects most of its wins, and they are substantive rather than cosmetic.
Rollback, conditional execution and diff are three genuinely different capabilities, not three names
for the same one.
The Spring Boot row is worth calling out as a tie because people assume otherwise. Both engines are
auto-configured, both run before JPA initialises, both are one property block away from working. Our
demo bypasses auto-configuration on purpose, to show the real bootstrap.
The timing row is the one place Flyway is ahead on observability, and it is a genuinely useful thing
to have when a deploy window is tight.
-->

---

## Feature matrix (3 of 3) — operations

| Capability | Flyway | Liquibase | Edge |
|---|---|---|---|
| History bookkeeping | version, description, checksum, timing, success | id, author, filename, contexts, labels, deployment id | Liquibase |
| Embedded status API | `flyway.info()` returns applied and pending as objects | No read API — query `DATABASECHANGELOG` | Flyway |
| Authorship tracking | Not recorded; attribution lives in version control | `author` is mandatory on every changeset | Liquibase |
| Checksum on applied changes | CRC32; editing an applied migration fails validation | MD5; same protection plus `runOnChange` opt-out | Tie |
| Merge conflict profile | Same version number in two branches collides loudly | Conflicts land in the master include list — easy to auto-merge wrongly | Flyway |
| Licensing of advanced features | `undo`, dry-run, drift detection are paid Teams/Enterprise | Core rollback and diff are open source; policy checks are paid Pro | Liquibase |

<!--
SPEAKER NOTES
Two rows here decide real procurement conversations.
The licensing row: the capabilities most teams eventually want — undo and drift detection — are paid
features in Flyway and open source in Liquibase. That is not a knock on Flyway's business model, but
it belongs in your evaluation, because "Flyway Community is enough" often stops being true about
eighteen months in.
The merge conflict row is the one experienced teams nod at. Two branches both adding V6 collide
immediately and obviously. Two branches both appending to a master changelog produce a conflict that
git will happily auto-resolve into the wrong order, and nothing fails until deploy.
-->

---

## The rollback story

**Flyway Community**: no undo. Reverting means a new forward migration.

```sql
-- V6__drop_product_audit_table.sql   (the only Community option)
DROP TABLE product_audit;
```

**Liquibase**: rollback is declared next to the change it reverses.

```xml
<rollback>
  <dropIndex tableName="product_audit" indexName="idx_product_audit_product"/>
  <dropTable tableName="product_audit"/>
</rollback>
```

`liquibase rollbackCount 1` is a supported operation. Every changeset in this repo declares one.

<!--
SPEAKER NOTES
Be honest about how much this matters in practice, because the honest answer is "less than the
marketing suggests, but not zero".
Most production incidents are not fixed by rolling the schema back. Once data has been written
against the new shape, an automated rollback destroys it. The disciplined pattern — expand, migrate,
contract — makes forward-only recovery viable, and plenty of high-functioning teams run Flyway
Community forever without missing undo.
Where rollback genuinely pays: pre-production. Tearing a test environment back to a known point,
rehearsing a release, iterating on a changeset locally. Being able to run rollbackCount 1 instead of
rebuilding the database is a real productivity gain, and it is free in Liquibase.
-->

---

## The portability story

- Flyway scripts are the target dialect. `AUTO_INCREMENT` is H2 and MySQL; PostgreSQL wants
  `GENERATED ALWAYS AS IDENTITY`. Changing database means rewriting the scripts.
- Liquibase changesets are abstract. `autoIncrement="true"` becomes whatever the target needs.
- But portability is not free: it holds only while you stay inside the tag vocabulary.
- The repo shows the seam — check constraints have no portable Liquibase tag, so `002` drops
  to raw `<sql>` and loses portability for those two statements.

<!--
SPEAKER NOTES
The nuance is the point of this slide. Liquibase's portability is real, and it is bounded.
Look at 002-create-product-table.yaml in the repo. The table, columns and indexes are portable tags.
The two check constraints are an inline <sql> block, with a comment saying exactly why: there is no
portable tag for them. So that file is portable in part and dialect-bound in part.
Then ask the room the question that actually settles it: how many times has your team changed
database engine? For most product teams the answer is zero, and portability is paying rent it does
not earn. For a vendor shipping the same product onto customer-chosen databases, it is the entire
reason to pick Liquibase.
-->

---

## Review and merge-conflict ergonomics

**Flyway**

- The pull request diff is SQL. Every reviewer already reads it.
- Two branches adding `V6__` collide on the filename — loud, immediate, unmissable.

**Liquibase**

- The diff is XML or YAML tags. The reviewer must know what each tag emits.
- Two branches appending to `db.changelog-master.yaml` conflict in the include list.
- Git can auto-merge that list into the wrong order. Nothing fails until deploy.

<!--
SPEAKER NOTES
This is the slide that changes minds in rooms full of practitioners, because it is about the daily
cost rather than the feature list.
The Flyway conflict is the good kind of failure: it happens at merge time, it is obvious, and the fix
is renaming a file. Some teams add a CI check that version numbers are unique — trivially cheap.
The Liquibase conflict is the bad kind: syntactically valid, semantically wrong, discovered later.
The mitigations are real — one changelog file per release, or directory-based inclusion with
includeAll — but they are conventions your team has to adopt and enforce, not defaults.
If your team is large and merges often, weigh this row heavily.
-->

---

## Decision guide

**Choose Flyway when**

- You target one database and expect to keep targeting it.
- Your team is fluent in SQL and wants review diffs it can read at a glance.
- Forward-only migration fits your release process.
- You want the smallest possible thing between intent and executed statement.

**Choose Liquibase when**

- You ship to several database engines, or your customers choose the engine.
- You need rollback, preconditions or per-environment conditional execution as first-class features.
- Audit requirements want author, context and deployment id inside the database.
- You want drift detection and diff without a commercial licence.

<!--
SPEAKER NOTES
This is the slide people photograph. Give it time.
Reframe the decision so nobody leaves thinking one tool is better. Flyway optimises for the common
case: one database, SQL-literate team, forward-only releases. Liquibase optimises for variability:
many databases, many environments, changes that must be conditional or reversible.
The most common mistake is picking Liquibase for portability you will never exercise, and paying the
review-ergonomics tax every single day for it. The second most common is picking Flyway Community and
discovering eighteen months later that undo and drift detection are behind a licence.
Whichever you choose: pick one, use it for everything, and never apply DDL by hand again. That
decision matters more than which name you pick.
-->

---

## Architecture of the demo app

```
DbMigrationComparisonApplication  (Spring Boot 3.4.2, Java 21, port 8080)
│
├── FlywayConfig      → flywayDataSource    → Flyway.migrate()   → flywayJdbcTemplate
├── LiquibaseConfig   → liquibaseDataSource → SpringLiquibase    → liquibaseJdbcTemplate
│
├── FlywayHistoryService    (flyway.info())          ─┐
├── LiquibaseHistoryService (SELECT … DATABASECHANGELOG) ├→ ComparisonService → ComparisonReport
├── SchemaInspectionService (INFORMATION_SCHEMA × 2)  ─┘
└── FeatureMatrix           (18 editorial rows)
```

`DataSourceAutoConfiguration` is excluded on purpose — both engines are wired explicitly.

<!--
SPEAKER NOTES
Trace the flow once, top to bottom. Two configs, two datasources, two migration runners, two
JdbcTemplates guarded by @DependsOn so nothing can read before migration completes.
Both history services implement the same MigrationHistoryProvider interface, which is what lets
ComparisonService treat the engines symmetrically. The implementations are asymmetric for a reason
that is itself a finding: one calls an API, the other writes SQL.
FeatureMatrix is a static immutable list, not a database table. It is documentation that happens to
be served over HTTP, and it changes only when the tools do.
Excluding DataSourceAutoConfiguration is deliberate: with two datasources and no @Primary ambiguity
resolved by Spring, explicit wiring is both correct and more instructive.
-->

---

## How to run it

```bash
git clone https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations
cd flyway-vs-liquibase-db-migrations
mvn spring-boot:run
```

| Endpoint | Shows |
|---|---|
| `/api/v1/comparison` | full side-by-side report + `schemasEquivalent` |
| `/api/v1/comparison/features` | the 18-row feature matrix |
| `/api/v1/migrations` | both engines' status |
| `/api/v1/migrations/{engine}/schema` | one engine's schema snapshot |
| `/api/v1/catalog/{engine}` | seeded data through `v_product_catalog` |
| `/swagger-ui.html` · `/h2-console` · `/api/v1/health` | docs, DB console, health |

<!--
SPEAKER NOTES
If you are demoing live, this is the moment. Start with /api/v1/comparison and scroll to
schemasEquivalent: true — that is the payoff of the whole talk in one field.
Then open /api/v1/migrations and put the two arrays side by side. The Flyway entries carry
executionTimeMs and author "n/a". The Liquibase entries carry a real author and a null
executionTimeMs. That contrast in raw JSON makes the bookkeeping slide concrete.
If you have the H2 console open, connect to jdbc:h2:file:./data/flywaydb and then ./data/liquibasedb
and show the two bookkeeping tables next to each other. Credentials are sa with an empty password.
Everything runs on a laptop with no Docker and no external database.
-->

---

## Questions

**Wallace Espindola** — Senior Software Engineer & Solution Architect

- Repository: [github.com/wallaceespindola/flyway-vs-liquibase-db-migrations](https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations)
- GitHub: [github.com/wallaceespindola](https://github.com/wallaceespindola)
- LinkedIn: [linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)

Clone it, run `mvn spring-boot:run`, and hit `/api/v1/comparison` yourself.

<!--
SPEAKER NOTES
Close on the one-line summary: both tools produce the same schema, so choose on process, not on
outcome.
Questions you should expect:
- "Can I migrate from Flyway to Liquibase?" Yes, in both directions. Liquibase can generate a
  changelog from an existing database, and you mark everything up to now as already-run.
- "What about using both?" Technically possible with separate schemas, but you now maintain two
  mental models. Do not.
- "Does this work outside Spring Boot?" Both have Maven, Gradle and CLI interfaces. Nothing in this
  comparison depends on Spring.
- "Which do you use?" Answer honestly, and say why in terms of the decision-guide slide.
-->
