**Suggested subject line:** I built both migration tools into the same app so I wouldn't have to guess

---

*Preview text: Everyone has a Flyway or Liquibase opinion. I got tired of mine, so I ran both against an identical schema and let the databases do the talking. Here's what actually held up.*

---

I've picked Flyway on some projects and Liquibase on others, and if I'm honest, the reasoning was never as rigorous as I'd like to admit. Whatever the team already knew, whatever the last architect set up, whatever felt faster to bootstrap on a Friday afternoon. That's how most of these decisions actually get made, and it bugged me enough that I finally built something to check my own assumptions.

So here's what I did. I wrote a small Spring Boot 3.4.2 app on Java 21 that stands up two completely separate H2 databases with the same logical schema — a category table, a product table, an audit trail, a view. One database gets migrated by Flyway. The other gets migrated by Liquibase. Neither one knows the other exists. Both are wired explicitly through their own `@Configuration` class, on purpose, so I could actually see what each tool does at startup instead of letting Spring Boot's auto-configuration paper over the differences.

Then I built an endpoint that reads `INFORMATION_SCHEMA` from both databases and diffs them: tables, views, columns, the works.

**The part that settled an argument I've had with myself for years**

Both schemas came back identical. `schemasEquivalent: true`, zero differences. Same tables, same columns, same view definition, byte for byte the same business schema, built by two tools that never talked to each other. Flyway applied six migrations to get there — five versioned SQL scripts and one repeatable view. Liquibase applied seven changesets across a master changelog that deliberately mixes XML, YAML and raw SQL formats, including one changeset with a precondition guard and an explicit rollback block.

If you've ever sat in a meeting where someone argued that one tool produces a "cleaner" or "more correct" schema than the other, you can drop that argument. Used properly, they don't differ there. The differences live somewhere quieter — in what each tool remembers about itself, and in how each one fails when two engineers step on each other.

**What each tool actually keeps track of, and what it doesn't**

Flyway ships something Liquibase simply doesn't have: an embedded, first-class status API. Call `flyway.info()` and you get every migration back — applied, pending, checksummed, ordered — as real objects, no SQL involved. I wanted the equivalent for Liquibase and there isn't one. If you're embedding Liquibase in an application rather than shelling out to its CLI, the honest way to read its history is to query `DATABASECHANGELOG` directly. I wrote that query myself in this project, and it's not a hack — it's genuinely the pattern Liquibase expects.

That same table, though, records things Flyway never asks for. Every Liquibase changeset requires an `author` attribute. It's mandatory — you can't skip it. Flyway has no such field; who wrote a given migration lives in your git blame and nowhere else inside the tool. Liquibase also persists contexts, labels and a deployment id per changeset, none of which Flyway tracks at all.

Flip that coin over and Flyway wins one back. It records execution time for every migration, in milliseconds, as a first-class field. Liquibase persists none. I checked the actual column list on `DATABASECHANGELOG` to be sure, and there's genuinely no duration stored anywhere. Each tool decided a different half of "operational history" was worth keeping, and you don't get both halves from either one.

**Rollback is where the philosophies actually split**

Liquibase changesets can carry an explicit `<rollback>` block, and for a lot of structural changes Liquibase infers one automatically without you writing anything. `rollbackCount 1` is a real, supported, tested command. In this project, changeset 004 — which adds an audit trail table — carries exactly that: a precondition that checks the `product` table exists before running, and a rollback block that drops the audit table cleanly if you need to back out.

Flyway Community has nothing like it. The equivalent Flyway migration in this project, the one that adds the same audit table, has a comment in the file that says it plainly: reverting this means writing a brand new forward migration by hand, because Flyway's actual `undo` command exists only in the paid Teams tier. That's not a knock on Flyway — its whole design philosophy is "migrations only move forward, and that's a feature, not a gap." But it is a real operational cost if your team leans on rollback as a safety net during deploys.

**The failure mode nobody puts in the comparison charts**

Here's the one I think matters most for teams working across branches. Flyway identifies every migration by a version number, and that number has to be unique and ordered. Two engineers on separate feature branches both writing `V6__something.sql` will collide, and Flyway's validation will refuse to run until someone fixes it. That's loud, it's immediate, and it happens before anything touches a real database.

Liquibase's conflict surface is quieter. Changesets get pulled into a master changelog through an include list, and two engineers each adding one new include line can merge in git without a single complaint. The actual conflict — wrong execution order, a missing changeset, something applied out of sequence — only shows up when someone runs the changelog for real, which might be well after the merge, possibly in CI, possibly in production if your pipeline is loose. I don't think this makes Liquibase worse. I think it means Liquibase asks your team to be more disciplined about changelog review, because the tool won't catch what git happily merged.

**One small thing both tools agree on**

There's a repeatable view in this schema — `v_product_catalog`, joining product to category. Flyway handles it with a file named `R__product_catalog_view.sql`: the `R__` prefix tells Flyway to re-run this script automatically whenever its checksum changes, no version bump required. Liquibase does the exact same thing with `runOnChange="true"` on a changeset. Same behavior, same intent, and the only real difference is where you declare it — baked into a filename on one side, an XML attribute on the other. It's a small detail, but it's a good reminder that the two tools converge more often than the "vs" framing suggests.

**What I'd pick**

If you'd asked me before I built this, I would have given you a hedge. Now I'd actually commit to an answer, and it depends on one question: does your team need rollback as an operational primitive, or not?

If the answer is no — if forward-only migrations are an acceptable discipline, your team is comfortable writing SQL directly, and you want merge conflicts to fail loud and early — I'd pick Flyway. The bootstrap is a DataSource and a location. Code review means reading SQL, which every engineer on the team already knows how to do without learning a tag vocabulary first.

If the answer is yes — if you need `rollbackCount` as a real deploy-time safety net, need database portability because your changelog has to run against more than one dialect, or need an audit trail that records who changed what and under which context — Liquibase earns its extra setup. The changelog composition step is more ceremony, but you're buying real capability with it, not just complexity for its own sake.

Neither tool is the "better" one in the abstract. They optimized for different failure modes, and once you know which failure mode scares your team more, the decision more or less makes itself.

The full project — both migration sets, the live comparison endpoint, and an 18-row feature matrix generated from running code — is on GitHub if you want to check any of this yourself rather than take my word for it: https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations

---

Wallace Espindola
GitHub: https://github.com/wallaceespindola/ · LinkedIn: https://www.linkedin.com/in/wallaceespindola/

Need more tech insights?
Check out my GitHub, LinkedIn, and Speaker Deck.
Happy coding!
