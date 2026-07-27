package com.wallaceespindola.dbmigration.service;

import com.wallaceespindola.dbmigration.dto.FeatureComparison;
import java.util.List;

/**
 * The editorial half of the comparison: capability-by-capability judgements that cannot be derived
 * by querying a database.
 *
 * <p>Kept as a single immutable list rather than a database table or config file — it is
 * documentation that happens to be served over HTTP, and it changes only when the tools do.
 *
 * @author Wallace Espindola
 */
public final class FeatureMatrix {

    private static final String FLYWAY = "FLYWAY";
    private static final String LIQUIBASE = "LIQUIBASE";
    private static final String TIE = "TIE";

    private static final List<FeatureComparison> ROWS = List.of(
            new FeatureComparison(
                    "Change format",
                    "Plain SQL only (Community); Java migrations for programmatic cases",
                    "XML, YAML, JSON or SQL — all first-class and mixable in one changelog",
                    LIQUIBASE),
            new FeatureComparison(
                    "Learning curve",
                    "Learn one naming convention: V1__name.sql. A SQL author is productive in minutes",
                    "Learn the changeset model, changelog composition and the tag vocabulary",
                    FLYWAY),
            new FeatureComparison(
                    "Database portability",
                    "None — scripts are written in the target dialect and must be rewritten per database",
                    "Changesets are abstract; Liquibase generates the dialect at runtime",
                    LIQUIBASE),
            new FeatureComparison(
                    "Rollback / undo",
                    "Not in Community — reverting means writing a new forward migration. undo is a Teams feature",
                    "Built in: auto-inferred for most changes, or declared with <rollback>",
                    LIQUIBASE),
            new FeatureComparison(
                    "Migration discovery",
                    "Convention over configuration — scans a location, orders by version",
                    "Explicit master changelog listing every included file",
                    FLYWAY),
            new FeatureComparison(
                    "Conditional execution",
                    "Placeholder substitution and per-environment locations; coarse-grained",
                    "Preconditions, contexts and labels evaluated per changeset",
                    LIQUIBASE),
            new FeatureComparison(
                    "Repeatable changes",
                    "R__ prefixed scripts, re-run automatically when their checksum changes",
                    "runOnChange=\"true\" on any changeset",
                    TIE),
            new FeatureComparison(
                    "History bookkeeping",
                    "flyway_schema_history — version, description, checksum, timing, success flag",
                    "DATABASECHANGELOG — id, author, filename, contexts, labels, deployment id, exec type",
                    LIQUIBASE),
            new FeatureComparison(
                    "Execution timing recorded",
                    "Yes, per migration, in milliseconds",
                    "No per-changeset duration is persisted",
                    FLYWAY),
            new FeatureComparison(
                    "Embedded status API",
                    "flyway.info() returns applied and pending migrations as objects",
                    "No lightweight read API — query DATABASECHANGELOG directly",
                    FLYWAY),
            new FeatureComparison(
                    "Concurrency safety",
                    "Database-level lock held for the duration of the migration run",
                    "Dedicated DATABASECHANGELOGLOCK table",
                    TIE),
            new FeatureComparison(
                    "Drift detection / diff",
                    "Not available in Community",
                    "diff and diffChangeLog compare two databases and generate the delta",
                    LIQUIBASE),
            new FeatureComparison(
                    "Spring Boot integration",
                    "Auto-configured from spring.flyway.*; migrates before JPA initialises",
                    "Auto-configured from spring.liquibase.*; same lifecycle position",
                    TIE),
            new FeatureComparison(
                    "Authorship tracking",
                    "Not recorded — attribution lives in version control only",
                    "author is a mandatory attribute of every changeset",
                    LIQUIBASE),
            new FeatureComparison(
                    "Checksum on applied changes",
                    "CRC32 of the script; editing an applied migration fails validation",
                    "MD5 of the changeset; same protection, plus explicit runOnChange opt-out",
                    TIE),
            new FeatureComparison(
                    "Review ergonomics",
                    "Diffs are SQL — every reviewer already reads it",
                    "Diffs are XML/YAML tags — reviewers must know what the tags emit",
                    FLYWAY),
            new FeatureComparison(
                    "Merge conflict profile",
                    "Two branches adding the same version number collide loudly and obviously",
                    "Conflicts land in the master changelog include list, which is easy to auto-merge wrongly",
                    FLYWAY),
            new FeatureComparison(
                    "Licensing of advanced features",
                    "undo, dry-run and drift detection are paid Teams/Enterprise features",
                    "Core rollback and diff are open source; policy checks and flows are paid Pro",
                    LIQUIBASE));

    private FeatureMatrix() {
        throw new AssertionError("Utility class");
    }

    /** The full feature matrix, immutable. */
    public static List<FeatureComparison> rows() {
        return ROWS;
    }
}
