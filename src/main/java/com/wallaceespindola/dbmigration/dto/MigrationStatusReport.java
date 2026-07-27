package com.wallaceespindola.dbmigration.dto;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Full migration status for one engine.
 *
 * @param engine which engine produced this report
 * @param engineDisplayName friendly engine name
 * @param historyTable bookkeeping table the engine maintains
 * @param appliedCount number of applied migrations
 * @param pendingCount number of migrations discovered but not yet applied
 * @param lastAppliedAt timestamp of the most recent migration, {@code null} if none
 * @param upToDate {@code true} when nothing is pending
 * @param migrations the applied migrations, oldest first
 * @author Wallace Espindola
 */
@Schema(description = "Migration status for a single engine")
public record MigrationStatusReport(
        MigrationEngine engine,
        String engineDisplayName,
        String historyTable,
        int appliedCount,
        int pendingCount,
        Instant lastAppliedAt,
        boolean upToDate,
        List<AppliedMigration> migrations) {}
