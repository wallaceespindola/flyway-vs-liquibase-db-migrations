package com.wallaceespindola.dbmigration.domain;

import java.util.Arrays;

/**
 * The two migration engines being compared.
 *
 * @author Wallace Espindola
 */
public enum MigrationEngine {

    FLYWAY("Flyway", "flyway_schema_history", "Versioned SQL scripts discovered by filename convention"),
    LIQUIBASE("Liquibase", "DATABASECHANGELOG", "Changesets declared in an explicit master changelog");

    private final String displayName;
    private final String historyTable;
    private final String model;

    MigrationEngine(String displayName, String historyTable, String model) {
        this.displayName = displayName;
        this.historyTable = historyTable;
        this.model = model;
    }

    public String displayName() {
        return displayName;
    }

    /** Name of the bookkeeping table the engine maintains inside the target database. */
    public String historyTable() {
        return historyTable;
    }

    public String model() {
        return model;
    }

    /**
     * Case-insensitive lookup used by the REST layer so {@code /api/v1/migrations/flyway} works as
     * well as {@code /api/v1/migrations/FLYWAY}.
     *
     * @throws IllegalArgumentException if no engine matches
     */
    public static MigrationEngine from(String value) {
        return Arrays.stream(values())
                .filter(engine -> engine.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown migration engine '%s'. Expected one of: flyway, liquibase".formatted(value)));
    }
}
