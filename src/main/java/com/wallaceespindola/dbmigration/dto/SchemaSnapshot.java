package com.wallaceespindola.dbmigration.dto;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The schema an engine actually produced, read back from {@code INFORMATION_SCHEMA}.
 *
 * <p>Engine bookkeeping tables are excluded from {@link #tables()} so the two snapshots can be
 * compared directly: if Flyway and Liquibase did their jobs, the business schema on both sides is
 * the same set of objects, and only {@link #bookkeepingTables()} differs.
 *
 * @param engine which engine built this schema
 * @param tables business tables, alphabetically sorted
 * @param views views, alphabetically sorted
 * @param columns {@code TABLE.COLUMN:TYPE} entries for business tables, alphabetically sorted
 * @param indexes indexes, alphabetically sorted — reported for information, not compared, because
 *     both H2 and the engines auto-generate constraint-backing indexes under differing names
 * @param bookkeepingTables tables the engine maintains for its own history
 * @author Wallace Espindola
 */
@Schema(description = "Schema objects produced by one engine, read from INFORMATION_SCHEMA")
public record SchemaSnapshot(
        MigrationEngine engine,
        List<String> tables,
        List<String> views,
        List<String> columns,
        List<String> indexes,
        List<String> bookkeepingTables) {}
