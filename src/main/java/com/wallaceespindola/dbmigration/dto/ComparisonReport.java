package com.wallaceespindola.dbmigration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The headline artefact of this project: both engines' status, both resulting schemas, whether those
 * schemas are equivalent, and the feature matrix.
 *
 * @param flyway Flyway's migration status
 * @param liquibase Liquibase's migration status
 * @param flywaySchema schema objects Flyway produced
 * @param liquibaseSchema schema objects Liquibase produced
 * @param schemasEquivalent {@code true} when both engines produced the same business schema
 * @param schemaDifferences human-readable differences, empty when equivalent
 * @param featureMatrix capability-by-capability comparison
 * @author Wallace Espindola
 */
@Schema(description = "Side-by-side comparison of both engines and the schemas they produced")
public record ComparisonReport(
        MigrationStatusReport flyway,
        MigrationStatusReport liquibase,
        SchemaSnapshot flywaySchema,
        SchemaSnapshot liquibaseSchema,
        boolean schemasEquivalent,
        List<String> schemaDifferences,
        List<FeatureComparison> featureMatrix) {}
