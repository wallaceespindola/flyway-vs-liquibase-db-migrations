package com.wallaceespindola.dbmigration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of the Flyway vs Liquibase feature matrix.
 *
 * @param feature the capability under comparison
 * @param flyway how Flyway handles it
 * @param liquibase how Liquibase handles it
 * @param edge which engine has the advantage: {@code FLYWAY}, {@code LIQUIBASE} or {@code TIE}
 * @author Wallace Espindola
 */
@Schema(description = "A single feature compared across both engines")
public record FeatureComparison(String feature, String flyway, String liquibase, String edge) {}
