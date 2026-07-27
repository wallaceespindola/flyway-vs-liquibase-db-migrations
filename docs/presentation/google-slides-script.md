# Delivery script — Flyway vs Liquibase

A per-slide talk track for presenting `flyway-vs-liquibase-deck.pptx` to a live audience,
plus timing plans for a 30-minute and a 45-minute slot, and instructions for importing the
deck into Google Slides.

Author: Wallace Espindola — [github.com/wallaceespindola](https://github.com/wallaceespindola) ·
[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)

---

## How to import this deck into Google Slides

**Option A — upload and open (keeps the deck as one unit)**

1. Go to [drive.google.com](https://drive.google.com) and click **New → File upload**.
2. Select `docs/presentation/flyway-vs-liquibase-deck.pptx`.
3. When the upload finishes, right-click the file → **Open with → Google Slides**.
4. Google Slides converts it in place. Use **File → Save as Google Slides** to keep an editable
   native copy; the original `.pptx` stays in Drive untouched.

**Option B — import slides into an existing presentation**

1. Upload the `.pptx` to Drive as above (no need to open it).
2. Open the target Google Slides presentation.
3. **File → Import slides → Google Drive**, pick the uploaded `.pptx`.
4. Select the slides you want and choose whether to **Keep original theme**. Keep it checked — the
   deck's dark slate theme and the Flyway/Liquibase accent colours are what make the comparison
   readable at a glance.

**After conversion, check these three things**

- **Code slides.** Google Slides substitutes Consolas if it is not available in your account.
  Set those text boxes to **Roboto Mono** or **Courier New** so the alignment survives.
- **Tables.** Row heights can shift by a pixel or two during conversion. The feature-matrix slides
  are the tightest — confirm no row wraps into a fourth line.
- **Speaker notes.** All 24 slides carry notes. Open **View → Show speaker notes** and confirm they
  came across before you present.

If a slide breaks in conversion, fix `generate_pptx.py` and re-export rather than editing in Google
Slides — the generator is the source of truth.

---

## Per-slide talk track

Each block is roughly 60–90 seconds at a normal speaking pace.

### 1 — Title

"Flyway versus Liquibase. Both tools solve the same problem, both are mature, and most comparisons
you will read online are written by someone who only ever used one of them.

So I built something different. There is a Spring Boot application behind this talk — Java 21,
Maven, H2, plain JDBC — that opens *two* independent databases. Flyway migrates one, Liquibase
migrates the other, and the application diffs the two resulting schemas at runtime and tells you
whether they match.

Everything in this deck is either code from that repository or a number the running application
produced. No benchmarks I made up, no adoption statistics. By the end you will have a measured
result and a decision framework, and I am not going to tell you which tool to pick — I am going to
tell you which trade-offs you are buying."

### 2 — The problem: schema drift and manual DDL

"Start with the problem, because if you have already solved it the rest of this talk is optional.

We version application code. We review it, we roll it forward through environments, and we can say
exactly what is running where. The schema very often gets none of that. It gets a script pasted into
a chat message and someone with production credentials.

The failure mode is not a dramatic outage. It is slow: dev has the column, staging does not,
production has it with a different type. You stop trusting that staging resembles production, so you
stop testing there, so defects reach users. And when someone asks 'which changes has this database
actually seen?', nobody can answer without reading the data.

The root cause is that DDL gets treated as an operational act instead of source code."

### 3 — Why migration tooling

"A migration tool gives you four things a wiki page of scripts cannot.

Ordering: changes apply in a defined sequence, once, everywhere. Bookkeeping: a table inside the
database itself records what has been applied — the database becomes self-describing. Integrity: a
checksum over each applied change, so editing a migration that has already run fails validation
instead of drifting silently. And automation: the migration runs at application startup or in the
pipeline, never by hand.

I want to dwell on the checksum for a second, because it is the one people underrate. It converts a
whole class of 'works on my machine' incidents into a startup failure with a clear message.

Both Flyway and Liquibase deliver all four. That is the baseline. Everything else in this talk is
about *how* — and the how is where the trade-offs live."

### 4 — How Flyway works

"Flyway's model fits on this slide, which is itself the point.

You drop a SQL file into a location and name it `V1__create_category_table.sql`. The filename is the
configuration: `V` for versioned, `1` for order, double underscore separator, the rest is a
description. An `R__` prefix instead means repeatable — Flyway re-applies it whenever the file's
checksum changes, which is what you want for views and stored procedures.

At startup Flyway scans the location, sorts by version, and runs whatever is not yet recorded in
`flyway_schema_history`. There is no registry, no include list, no changelog to maintain. Discovery
is a directory scan plus an ordering rule.

The code at the bottom is from `FlywayConfig.java` in the repository, unedited. Five builder calls
and the engine is live. Note `validateOnMigrate(true)` — that is checksum enforcement — and in the
repo `cleanDisabled(true)`, which blocks the destructive clean command. Switch that off anywhere
that is not a laptop."

### 5 — How Liquibase works

"Liquibase models the same problem one level up.

The unit is a *changeset*, identified by id plus author plus source file. Changesets live in XML,
YAML, JSON or SQL — all four are first-class and you can mix them inside one changelog. A master
changelog explicitly lists every included file, so ordering is declared rather than inferred from
filenames.

The important bit is that changesets are database-agnostic. You write `createTable`, not
`CREATE TABLE`, and Liquibase decides what H2, PostgreSQL or Oracle should actually receive. That
abstraction is the product. It is what unlocks portability — and it is also what makes a pull request
harder to review for someone who only speaks SQL.

On top of that, each changeset carries attributes: preconditions, contexts, labels, rollback,
runOnChange. Those become important later.

The bootstrap is from `LiquibaseConfig.java`. More knobs than Flyway needed, and that is not an
accident — an abstract model has to be told what to include, in what context, against which schema."

### 6 — The experiment design

"Here is how the comparison is set up, because the design is what makes the result trustworthy.

One JVM, port 8080. Two H2 database files: `./data/flywaydb` and `./data/liquibasedb`. Two
`@Configuration` classes, each owning its own datasource, its own migration runner and its own
JdbcTemplate.

Two details worth calling out. First, Spring Boot's `DataSourceAutoConfiguration` is excluded on
purpose. I did not want the talk to be about auto-configuration magic — you see the real bootstrap
of each engine. Second, each JdbcTemplate carries `@DependsOn` on its migration bean. That is what
guarantees nothing can read the database before migrations have finished, so the comparison always
reflects a fully migrated state.

Six Flyway migrations on one side. Seven Liquibase changesets on the other. Then the application
reads both schemas out of `INFORMATION_SCHEMA` — it never trusts the migration scripts, it reads
what the database actually contains — and diffs them."

### 7 — Flyway migrations walkthrough

"Six files on the Flyway side, and they cover the realistic lifecycle of a schema.

V1 creates `category` with a unique constraint and an index. V2 creates `product` with a foreign key,
two check constraints and two indexes. V3 seeds reference data — three categories, five products.

That third one is a deliberate choice worth defending: seeding through a *versioned* migration means
the seed becomes part of schema history and applies exactly once per environment, in order. For
reference data that is the right call.

V4 adds an audit table and backfills it from `product`. V5 is the classic expand step of an
expand/contract rollout: add a column with a default, backfill it, index it — one logical change, one
migration.

And `R__product_catalog_view.sql` has no version number at all. It is repeatable: Flyway re-runs it
whenever the checksum changes. Six migrations applied in total."

### 8 — Flyway code

"This is the ergonomic argument for Flyway, in two snippets.

V5 on the top. Add the column, backfill it, index it. Anyone in this room who reads SQL can review
that in five seconds with no additional vocabulary. There is nothing between the intent and the
statement the database executes.

The repeatable view below it: `CREATE OR REPLACE VIEW`, exactly as you would write it by hand.

Now the cost, which is on the same slide if you look for it. This is H2 dialect. `BOOLEAN NOT NULL
DEFAULT TRUE` happens to survive a move to PostgreSQL — but `AUTO_INCREMENT` back in V1 does not.
Change database engine and you rewrite these files.

And notice what is *absent* from V5: any way to undo it. Flyway Community has no undo. Reverting
this means writing V6. Hold that thought."

### 9 — Liquibase changelog walkthrough

"Six changelog files, deliberately written in three different formats.

001 is XML: a portable `createTable` with an explicit rollback block. 002 is the same table in YAML —
identical semantics, terser to diff. 003 is raw SQL with Liquibase comment directives, which is what
you use when you need vendor-specific behaviour but still want changeset tracking.

That mixing is not showing off. It is how real Liquibase projects look: structural changes in XML or
YAML for portability, raw SQL only where a feature has no portable tag. 002 does exactly that — check
constraints have no Liquibase tag, so there is an inline SQL block for them with a comment saying
why.

004 demonstrates preconditions and rollback. 005 demonstrates contexts and labels, and it contains
*two* changesets — the column addition and the data backfill, split apart with different labels so
they can be selected independently. 006 is `runOnChange`.

Six files, seven changesets, because of that split in 005."

### 10 — Liquibase code: preconditions and rollback

"This is the changeset with no Flyway Community equivalent, and there are two separate capabilities
on display.

First, preconditions. `tableExists` guards the change with `onFail="MARK_RAN"` — if the `product`
table is not there, this changeset is recorded as run instead of exploding halfway through a deploy.
`onFail` has several modes: MARK_RAN, CONTINUE, HALT, WARN. You choose the failure semantics per
changeset. Flyway has no declarative equivalent; you would write defensive SQL or drop into a Java
migration.

Second, the rollback block. This is what makes `liquibase rollbackCount 1` a supported, testable
operation rather than a hope. Liquibase can infer rollbacks for most structural changes automatically
— but declaring them explicitly keeps the intent visible and survives someone refactoring the forward
change later.

Every changeset in this repository declares one."

### 11 — Side by side: the same table, two ways

"Same table, two philosophies, and the trade-off is visible in the character count.

The Flyway version is four lines of SQL. Shorter, and every reviewer in your organisation already
speaks it fluently.

The Liquibase version is longer and it asks the reviewer to know what the tags emit. In exchange it
names its constraints explicitly — `pk_category`, `uk_category_name` — and it is not bound to H2.
`AUTO_INCREMENT` in the SQL version is H2 and MySQL syntax; PostgreSQL wants `GENERATED ALWAYS AS
IDENTITY`. Liquibase's `autoIncrement="true"` produces the correct thing on either.

That is the entire comparison in miniature: directness versus abstraction. Neither one is free."

### 12 — Bookkeeping compared

"Both engines write a table into your database. They record different things, and this slide is
measured — this is what the two tables actually contain in the running demo.

Flyway identifies a migration by version, in one globally ordered namespace. Liquibase uses id plus
author plus filename. Flyway does not record who wrote a migration; Liquibase makes author a
mandatory attribute. Checksums are CRC32 and MD5 respectively — same protection either way.

Two rows point in opposite directions and both matter.

Flyway records execution time per migration, in milliseconds. Liquibase does not persist a
per-changeset duration at all. If you need to know which migration is making your deploy window
uncomfortable, Flyway tells you for free.

Liquibase records contexts, labels and a deployment id. Flyway's attribution lives only in git. If
your auditors want to see who changed the schema without leaving the database, Liquibase answers that
and Flyway does not."

### 13 — Reading the history: two APIs

"This is the finding that surprised me most while building the demo, and it is a real operational
difference if you embed the engine inside an application.

`flyway.info().all()` hands you objects. Applied and pending, already ordered, with state, checksum
and execution time attached. Zero SQL. `FlywayHistoryService` in the repository is essentially a
stream over that array.

Liquibase has no equivalent. `LiquibaseHistoryService` is a JdbcTemplate query straight against
`DATABASECHANGELOG`, because the table *is* the public interface. It works, and that table gives you
more columns than Flyway's does — but you are now coupled to Liquibase's internal table shape, and
that is a coupling nobody chose deliberately.

One more honest note: Liquibase only records what it has already run. Discovering *pending*
changesets requires a full changelog parse, which is why the status endpoint in this demo reports
pending as zero on that side."

### 14 — The measured result

"So: six Flyway migrations, seven Liquibase changesets. Do they land in the same place?

`GET /api/v1/comparison` on the running application returns `schemasEquivalent: true` and an empty
differences array.

Both sides have `category`, `product` and `product_audit`. Both have the `v_product_catalog` view.
Every column is compared as `TABLE.COLUMN:TYPE` and there are zero differences.

Let me be precise about what is compared and what is not, because the honesty matters more than the
headline. The diff covers tables, views and columns. It deliberately excludes indexes, because H2
auto-generates constraint-backing indexes under generated names that legitimately differ between the
two engines — including them would report noise as drift. That exclusion is documented in the code,
not buried.

The only real difference is bookkeeping: `flyway_schema_history` on one side,
`DATABASECHANGELOG` and `DATABASECHANGELOGLOCK` on the other.

So the choice between these tools is not about what schema you end up with. It is entirely about the
process of getting there."

### 15 — Feature matrix, part 1: authoring

"Eighteen rows, split across three slides. This matrix lives in `FeatureMatrix.java` and is served
live at `/api/v1/comparison/features`. Everything left of the 'Edge' column is factual; the Edge
column is my judgement and I am labelling it as such.

Part one is about authoring — what it is like to write and review a change.

Liquibase takes change format and portability. Flyway takes learning curve, discovery and review
ergonomics. Repeatable changes are a tie: `R__` prefix versus `runOnChange="true"`, same semantics,
different place to declare it.

You can already see the pattern that runs through all eighteen rows: Flyway wins where humans read
and write changes, Liquibase wins where machines execute them across varied environments."

### 16 — Feature matrix, part 2: execution

"Part two is execution, and this is where Liquibase collects most of its wins — substantively, not
cosmetically.

Rollback, conditional execution and diff are three genuinely different capabilities. Preconditions
and contexts are per-changeset and evaluated at runtime; Flyway's nearest equivalent is placeholder
substitution or separate migration locations per environment, which is coarser and resolved at the
filesystem level.

Two ties worth naming. Concurrency: both engines lock, they just lock differently. And Spring Boot
integration — people assume one is better here and they are wrong. Both are auto-configured, both run
before JPA initialises, both are one property block from working. This demo bypasses
auto-configuration deliberately, to show the real bootstrap.

Flyway's win on this slide is execution timing, and it is the one place it is ahead on
observability."

### 17 — Feature matrix, part 3: operations

"Part three is operations, and two of these rows decide real procurement conversations.

Licensing first. The capabilities most teams eventually want — undo and drift detection — are paid
Teams or Enterprise features in Flyway, and open source in Liquibase. That is not a criticism of
Flyway's business model, but it belongs in your evaluation, because 'Flyway Community is enough'
tends to stop being true about eighteen months in.

Then merge conflicts, which is the row experienced teams nod at. Two branches both adding V6 collide
immediately, obviously, at merge time. Two branches both appending to a master changelog produce a
conflict that git will cheerfully auto-resolve into the wrong order — and nothing fails until deploy.

The rest: Liquibase's history table records more, Flyway has the status API, checksums are a tie."

### 18 — The rollback story

"Let me be honest about rollback, because the honest answer is 'it matters less than the marketing
suggests, and more than zero'.

Flyway Community has no undo. Reverting the audit table means writing V6 that drops it. That is the
only option.

Liquibase declares the rollback next to the change it reverses, and `rollbackCount 1` is a supported
operation.

Now the reality check. Most production incidents are not fixed by rolling the schema back. Once data
has been written against the new shape, an automated rollback destroys it. The disciplined pattern —
expand, migrate, contract — makes forward-only recovery viable, and plenty of high-functioning teams
run Flyway Community indefinitely without ever missing undo.

Where rollback genuinely earns its keep is pre-production: tearing a test environment back to a known
point, rehearsing a release, iterating on a changeset locally. Running `rollbackCount 1` instead of
rebuilding the database is a real productivity gain, and in Liquibase it costs nothing."

### 19 — The portability story

"Same treatment for portability, because the nuance is the whole slide.

Liquibase's portability is real. `autoIncrement="true"` becomes whatever the target database needs.
Flyway's scripts are the target dialect and you would rewrite them.

But it is bounded, and the repository shows exactly where the boundary sits. Look at 002: table,
columns and indexes are portable tags. The two check constraints are an inline SQL block, with a
comment explaining that there is no portable tag for them. So that one file is portable in part and
dialect-bound in part. Every real Liquibase project has files that look like this.

Then the question that actually settles it: how many times has your team changed database engine?
For most product teams the answer is zero, and portability is paying rent it never earns. For a
vendor shipping the same product onto whatever database the customer already runs, it is the entire
reason to pick Liquibase."

### 20 — Review and merge-conflict ergonomics

"This is the slide about daily cost rather than feature lists, and in my experience it changes more
minds than the feature matrix does.

On the Flyway side the pull request diff is SQL. Every reviewer already reads it. And when two
branches both add V6, they collide on the filename — loud, immediate, unmissable, and the fix is
renaming a file. Some teams add a CI check that version numbers are unique, which costs about ten
lines.

On the Liquibase side the diff is tags, so the reviewer has to know what each one emits. And when two
branches both append to the master changelog, the conflict lands in the include list. Git will
happily auto-merge that into a valid file with the wrong order. Nothing fails until deploy.

The mitigations are real — one changelog file per release, or `includeAll` on a directory — but they
are conventions your team has to adopt and enforce, not defaults you get for free.

If your team is large and merges often, weigh this heavily."

### 21 — Decision guide

"So here is the framework. Photograph this one.

Choose Flyway when you target one database and expect to keep targeting it, when your team is fluent
in SQL and wants review diffs it can read at a glance, when forward-only migration fits your release
process, and when you want the smallest possible thing between intent and executed statement.

Choose Liquibase when you ship to several database engines or your customers choose the engine, when
you need rollback or preconditions or conditional execution as first-class features, when audit
requirements want author and context and deployment id inside the database, and when you want drift
detection and diff without buying a licence.

The two most common mistakes I see: picking Liquibase for portability you will never exercise and
paying the review tax every single day for it — and picking Flyway Community, then discovering
eighteen months later that undo and drift detection sit behind a paywall.

And whichever you pick: pick one, use it for everything, and never apply DDL by hand again. That
decision matters far more than which of these two names you choose."

### 22 — Architecture of the demo app

"Thirty seconds on how the demo is put together, in case you want to fork it.

Two configs, two datasources, two migration runners, two JdbcTemplates guarded by `@DependsOn`.

Both history services implement the same `MigrationHistoryProvider` interface, which is what lets
`ComparisonService` treat the two engines symmetrically. The *implementations* are asymmetric, and
that asymmetry is itself a finding: one calls an API, the other writes SQL.

`SchemaInspectionService` reads `INFORMATION_SCHEMA` on both sides. `FeatureMatrix` is a static
immutable list — documentation that happens to be served over HTTP, and it changes only when the
tools do.

`DataSourceAutoConfiguration` is excluded deliberately. With two datasources, explicit wiring is both
correct and considerably more instructive."

### 23 — How to run it

"Clone it, `mvn spring-boot:run`, done. No Docker, no external database, H2 writes two files into
`./data`.

`/api/v1/comparison` is the one to hit first — scroll to `schemasEquivalent` and there is the payoff
of this entire talk in one field.

`/api/v1/migrations` gives you both engines' history side by side, and I recommend actually looking
at the raw JSON: the Flyway entries carry `executionTimeMs` and an author of `n/a`; the Liquibase
entries carry a real author and a null `executionTimeMs`. That contrast makes the bookkeeping slide
concrete in a way the table cannot.

There is Swagger UI at `/swagger-ui.html`, and the H2 console at `/h2-console` if you want to open
both bookkeeping tables yourself — `sa` with an empty password."

### 24 — Questions

"That is the talk. One sentence to take away: both tools produce the same schema, so choose on
process, not on outcome.

The repository link is on screen. Clone it, run it, and disagree with my Edge column — the matrix is
one Java file and I would genuinely like the pull request.

Questions."

**Anticipate these:**

- *"Can I migrate from Flyway to Liquibase, or back?"* — Yes, both directions. Liquibase can generate
  a changelog from an existing database; you then mark everything up to now as already-run. Going the
  other way, you baseline Flyway at the current schema version.
- *"Can I use both?"* — Technically yes, with separate schemas. Do not. You now maintain two mental
  models and two bookkeeping tables for one problem.
- *"Does any of this depend on Spring Boot?"* — No. Both have Maven, Gradle and CLI interfaces. The
  Spring integration is convenience, not substance.
- *"Which do you actually use?"* — Answer honestly and justify it in terms of slide 21, not taste.
- *"What about Flyway's Java migrations?"* — Real, useful for programmatic changes that SQL cannot
  express. They do not change the portability or rollback story.

---

## Timing guide

### 45-minute version (full deck, 24 slides)

| Segment | Slides | Minutes | Running |
|---|---|---|---|
| Framing | 1–3 | 4 | 4 |
| How each tool works | 4–5 | 4 | 8 |
| Experiment design | 6 | 2 | 10 |
| Flyway walkthrough | 7–8 | 4 | 14 |
| Liquibase walkthrough | 9–10 | 4 | 18 |
| Side by side | 11 | 2 | 20 |
| Bookkeeping + APIs | 12–13 | 4 | 24 |
| **The measured result** | 14 | 3 | 27 |
| Feature matrix | 15–17 | 5 | 32 |
| Rollback, portability, review | 18–20 | 5 | 37 |
| Decision guide | 21 | 3 | 40 |
| Architecture + how to run | 22–23 | 2 | 42 |
| Q&A | 24 | 3+ | 45 |

Live demo fits here: spend 3 extra minutes on slide 23 and trim the feature matrix to two slides.

### 30-minute version

Cut to 18 slides. **Drop 3 (why tooling), 8 (Flyway code detail), 16 (matrix part 2), 19
(portability), 22 (architecture), 23 (how to run — put the URL on slide 24).**

| Segment | Slides | Minutes | Running |
|---|---|---|---|
| Framing | 1–2 | 3 | 3 |
| How each tool works | 4–5 | 4 | 7 |
| Experiment design | 6 | 2 | 9 |
| Flyway walkthrough | 7 | 2 | 11 |
| Liquibase walkthrough | 9–10 | 3 | 14 |
| Side by side | 11 | 2 | 16 |
| Bookkeeping + APIs | 12–13 | 3 | 19 |
| **The measured result** | 14 | 3 | 22 |
| Feature matrix (1 and 3) | 15, 17 | 3 | 25 |
| Rollback + review ergonomics | 18, 20 | 2 | 27 |
| Decision guide + close | 21, 24 | 3 | 30 |

Q&A goes in the hallway. Say so at the start so nobody saves questions for a slot that does not
exist.

### Non-negotiables in any version

Slides **6** (experiment design), **14** (the measured result) and **21** (decision guide). Without 6
the result is not credible; without 14 there is no evidence; without 21 nobody leaves with a
decision. Everything else can be cut.

### Pacing notes

- Slides 15–17 are reference material. Read two or three rows out loud and move on — do not narrate
  eighteen rows.
- Slides 8, 10 and 11 are code. Give the room five seconds of silence to read before you speak.
- Slide 14 is the beat of the talk. Pause after saying `schemasEquivalent: true`.
- If you are running long, cut slide 19 (portability) before anything else — it is the one point
  audiences already intuit.
