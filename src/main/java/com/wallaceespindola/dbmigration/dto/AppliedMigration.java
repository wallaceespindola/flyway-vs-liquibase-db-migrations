package com.wallaceespindola.dbmigration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One applied migration, normalised across both engines so the dashboard can render a single table
 * shape for either side.
 *
 * <p>The normalisation itself is informative: Flyway identifies a migration by <em>version</em>
 * (globally ordered, one namespace), while Liquibase identifies a changeset by the composite
 * <em>id + author + filename</em>. Mapping both into {@code identifier} is where that difference
 * becomes concrete.
 *
 * @param identifier Flyway version, or Liquibase {@code id::author} pair
 * @param description what the migration does
 * @param script source file the change came from
 * @param type engine-specific kind (SQL, XML, YAML, EXECUTED, RERAN, ...)
 * @param author who authored it — always {@code n/a} for Flyway, which does not record authorship
 * @param checksum integrity checksum used to detect edits to already-applied migrations
 * @param appliedAt when it was applied
 * @param executionTimeMs how long it took, {@code null} when the engine does not record it
 * @param status engine-reported state
 * @author Wallace Espindola
 */
@Schema(description = "A single applied migration, normalised across Flyway and Liquibase")
public record AppliedMigration(
        String identifier,
        String description,
        String script,
        String type,
        String author,
        String checksum,
        Instant appliedAt,
        Long executionTimeMs,
        String status) {}
