I ran the same schema through Flyway and Liquibase. Here's what actually differed.

Every "Flyway vs Liquibase" thread turns into people quoting scars from their last project. I got tired of reading opinions, so I built both tools into the same Spring Boot app and let the databases settle the argument.

**Why this matters more than it looks like it should**

Your migration tool is one of the few architecture decisions you're stuck with for the life of the project. Nobody rewrites their migration history once production data depends on it. Teams usually pick Flyway or Liquibase because that's what the last senior engineer used, not because anyone actually compared what the tools produce or what they cost you operationally. That's a bad way to lock in a multi-year decision.

**The setup**

I built a small Spring Boot 3.4.2 app on Java 21 that runs two completely independent H2 databases with an identical logical schema. One database is migrated by Flyway, the other by Liquibase, both wired explicitly through their own `@Configuration` class — no Spring Boot autoconfiguration magic hiding what each tool actually does at startup.

Flyway runs five versioned SQL scripts plus one repeatable migration for a view — six migrations applied. Liquibase runs a master changelog that mixes XML, YAML and raw SQL changesets, including preconditions, an explicit rollback block, and contexts and labels — seven changesets applied. Same business schema, two different tools, two separate databases that never talk to each other.

The app then exposes an endpoint that reads `INFORMATION_SCHEMA` on both databases and diffs tables, views and columns at runtime.

**The result surprised exactly no one who's used both tools seriously**

`schemasEquivalent: true`. Zero differences. Both engines land on the identical business schema — same tables, same columns, same view definition. If your team is still debating "which tool builds a better schema," that's the wrong question. Used correctly, both build the same one. The differences that matter live somewhere else entirely: in what each tool tells you about itself after the fact, and in how each one fails.

**Where the tools actually diverge**

Flyway ships a first-class embedded status API. Call `flyway.info()` and you get every applied and pending migration back as structured objects — no SQL required. Liquibase has no equivalent. Reading its history means querying its bookkeeping table directly:

```java
private static final String HISTORY_QUERY = """
    SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE,
           MD5SUM, DESCRIPTION, COMMENTS, CONTEXTS, LABELS, DEPLOYMENT_ID
    FROM DATABASECHANGELOG
    ORDER BY ORDEREXECUTED
    """;
```

That table is also where Liquibase wins something back. Every changeset requires an `author` attribute, and Liquibase persists contexts, labels and a deployment id alongside it. Flyway records none of that — authorship lives in your git history or nowhere. Flip side: Flyway records per-migration execution time in milliseconds. Liquibase persists no duration at all. Each tool decided a different axis of "history" was worth storing, and neither one stores both.

Rollback is the sharpest divide. Liquibase changesets can carry an explicit `<rollback>` block, and most structural changes get one inferred automatically — `rollbackCount 1` is a real, tested command. Flyway Community has nothing comparable. Reverting a Flyway migration means writing a new forward migration that undoes the last one. Flyway's actual `undo` command exists, but it's a paid Teams feature.

And then there's the one place the two tools quietly agree: repeatable objects like views. Flyway handles it with an `R__` filename prefix that re-runs whenever its checksum changes. Liquibase does the identical thing with `runOnChange="true"` on any changeset. Same behavior, declared in two different places — a filename convention versus an XML attribute.

**The failure mode nobody mentions in the marketing pages**

Two engineers on separate branches both add `V6__something.sql`. Flyway's validation catches that instantly and loudly — you cannot deploy until it's fixed. Now picture the Liquibase equivalent: two engineers both add an `include` line to the master changelog. Git might merge that cleanly. Nothing fails at merge time. The conflict — wrong ordering, a missing changeset, a duplicate — only shows up when someone actually runs the changelog. Flyway fails fast and ugly. Liquibase can fail quiet and late.

**What I'd tell a tech lead making this call**

If your team writes SQL fluently, wants code review to mean reading actual SQL diffs instead of decoding XML or YAML tags, and values a merge conflict that screams at you before it reaches production — Flyway fits that team.

If you need rollback as a real operational primitive instead of "write another migration and hope," need to target more than one database dialect from the same changelog, or need an audit trail that records who changed what and under which context — Liquibase earns its extra ceremony.

Neither tool is more correct. They optimized for different failure modes, and that's the actual decision you're making, not schema quality.

The full comparison — a live app, both migration sets, and an 18-row feature matrix generated from running code, not from a blog post — is on GitHub: https://github.com/wallaceespindola/flyway-vs-liquibase-db-migrations

What's driving your team's migration tool choice — the SQL, the rollback story, or just whatever the last person to set up the project already knew? Let me know your thoughts in the comments.

—

Wallace Espindola, Senior Software Engineer & Solution Architect
GitHub: https://github.com/wallaceespindola/
LinkedIn: https://www.linkedin.com/in/wallaceespindola/

Need more tech insights?
Check out my GitHub, LinkedIn, and Speaker Deck.
Happy coding!
