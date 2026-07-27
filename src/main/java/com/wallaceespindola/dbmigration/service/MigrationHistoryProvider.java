package com.wallaceespindola.dbmigration.service;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.MigrationStatusReport;

/**
 * Reads applied-migration history from one engine and normalises it into a common shape.
 *
 * <p>Two genuinely different implementations sit behind this interface, and the difference is itself
 * a finding: {@link FlywayHistoryService} reads history through Flyway's public {@code info()} API,
 * whereas {@link LiquibaseHistoryService} has to query the {@code DATABASECHANGELOG} table directly
 * because Liquibase exposes no comparable read-only status API for embedded use.
 *
 * @author Wallace Espindola
 */
public interface MigrationHistoryProvider {

    /** The engine this provider reports on. */
    MigrationEngine engine();

    /** Current migration status, migrations ordered oldest first. */
    MigrationStatusReport status();
}
