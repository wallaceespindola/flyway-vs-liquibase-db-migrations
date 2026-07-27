package com.wallaceespindola.dbmigration.service;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.ComparisonReport;
import com.wallaceespindola.dbmigration.dto.MigrationStatusReport;
import com.wallaceespindola.dbmigration.dto.SchemaSnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Composes the side-by-side report: each engine's status, each engine's resulting schema, and a
 * structural diff proving whether the two engines converged on the same business schema.
 *
 * @author Wallace Espindola
 */
@Slf4j
@Service
public class ComparisonService {

    private final Map<MigrationEngine, MigrationHistoryProvider> providers;
    private final SchemaInspectionService schemaInspection;

    public ComparisonService(
            List<MigrationHistoryProvider> providers, SchemaInspectionService schemaInspection) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(
                        MigrationHistoryProvider::engine,
                        Function.identity(),
                        (first, second) -> first,
                        () -> new EnumMap<>(MigrationEngine.class)));
        this.schemaInspection = schemaInspection;
        log.info("Comparison wired for engines: {}", this.providers.keySet());
    }

    /** Migration status for a single engine. */
    public MigrationStatusReport status(MigrationEngine engine) {
        MigrationHistoryProvider provider = providers.get(engine);
        if (provider == null) {
            throw new IllegalArgumentException("No history provider registered for engine " + engine);
        }
        return provider.status();
    }

    /** Schema objects a single engine produced. */
    public SchemaSnapshot schema(MigrationEngine engine) {
        return schemaInspection.snapshot(engine);
    }

    /** The full side-by-side comparison. */
    public ComparisonReport compare() {
        SchemaSnapshot flywaySchema = schema(MigrationEngine.FLYWAY);
        SchemaSnapshot liquibaseSchema = schema(MigrationEngine.LIQUIBASE);
        List<String> differences = diff(flywaySchema, liquibaseSchema);

        return new ComparisonReport(
                status(MigrationEngine.FLYWAY),
                status(MigrationEngine.LIQUIBASE),
                flywaySchema,
                liquibaseSchema,
                differences.isEmpty(),
                differences,
                FeatureMatrix.rows());
    }

    /**
     * Structural diff of two schemas over tables, views and columns.
     *
     * <p>Indexes are deliberately excluded: H2 auto-generates constraint-backing indexes under
     * generated names that legitimately differ between the two engines, so including them would
     * report noise as drift.
     */
    static List<String> diff(SchemaSnapshot flyway, SchemaSnapshot liquibase) {
        List<String> differences = new ArrayList<>();
        compareSets("table", flyway.tables(), liquibase.tables(), differences);
        compareSets("view", flyway.views(), liquibase.views(), differences);
        compareSets("column", flyway.columns(), liquibase.columns(), differences);
        return List.copyOf(differences);
    }

    private static void compareSets(
            String kind, List<String> flywayItems, List<String> liquibaseItems, List<String> sink) {
        new TreeSet<>(flywayItems).stream()
                .filter(item -> !liquibaseItems.contains(item))
                .forEach(item -> sink.add("%s '%s' exists in the Flyway schema but not in the Liquibase schema"
                        .formatted(kind, item)));

        new TreeSet<>(liquibaseItems).stream()
                .filter(item -> !flywayItems.contains(item))
                .forEach(item -> sink.add("%s '%s' exists in the Liquibase schema but not in the Flyway schema"
                        .formatted(kind, item)));
    }
}
