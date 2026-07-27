package com.wallaceespindola.dbmigration.service;

import com.wallaceespindola.dbmigration.config.LiquibaseConfig;
import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.AppliedMigration;
import com.wallaceespindola.dbmigration.dto.MigrationStatusReport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Reads Liquibase's history straight out of the {@code DATABASECHANGELOG} table.
 *
 * <p>This is the honest way to do it when Liquibase is embedded in an application: unlike Flyway,
 * Liquibase offers no lightweight read-only status API, so the bookkeeping table becomes the public
 * interface. In exchange that table records considerably more than Flyway's — author, contexts,
 * labels, deployment id and exec type all live here, which is what makes Liquibase's audit trail
 * richer.
 *
 * @author Wallace Espindola
 */
@Slf4j
@Service
public class LiquibaseHistoryService implements MigrationHistoryProvider {

    private static final String HISTORY_QUERY =
            """
            SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE,
                   MD5SUM, DESCRIPTION, COMMENTS, CONTEXTS, LABELS, DEPLOYMENT_ID
            FROM DATABASECHANGELOG
            ORDER BY ORDEREXECUTED
            """;

    private final JdbcTemplate jdbcTemplate;

    public LiquibaseHistoryService(
            @Qualifier(LiquibaseConfig.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MigrationEngine engine() {
        return MigrationEngine.LIQUIBASE;
    }

    @Override
    public MigrationStatusReport status() {
        List<AppliedMigration> applied = jdbcTemplate.query(HISTORY_QUERY, CHANGELOG_ROW_MAPPER);
        log.debug("Liquibase reported {} applied changesets", applied.size());

        Instant lastApplied = applied.isEmpty() ? null : applied.getLast().appliedAt();

        // Liquibase only records what it has already run. Discovering pending changesets requires a
        // full changelog parse, which is deliberately out of scope for a read-only status endpoint —
        // by the time the application has started, SpringLiquibase has applied everything anyway.
        return new MigrationStatusReport(
                MigrationEngine.LIQUIBASE,
                MigrationEngine.LIQUIBASE.displayName(),
                MigrationEngine.LIQUIBASE.historyTable(),
                applied.size(),
                0,
                lastApplied,
                true,
                applied);
    }

    private static final RowMapper<AppliedMigration> CHANGELOG_ROW_MAPPER =
            (ResultSet rs, int rowNum) -> {
                Timestamp executedAt = rs.getTimestamp("DATEEXECUTED");
                return new AppliedMigration(
                        "%s::%s".formatted(rs.getString("ID"), rs.getString("AUTHOR")),
                        describe(rs),
                        rs.getString("FILENAME"),
                        rs.getString("EXECTYPE"),
                        rs.getString("AUTHOR"),
                        rs.getString("MD5SUM"),
                        executedAt == null ? null : executedAt.toInstant(),
                        null, // Liquibase does not record per-changeset execution time
                        rs.getString("EXECTYPE"));
            };

    /** Prefer the changeset's own comment, falling back to Liquibase's generated description. */
    private static String describe(ResultSet rs) throws SQLException {
        String comments = rs.getString("COMMENTS");
        return comments != null && !comments.isBlank() ? comments : rs.getString("DESCRIPTION");
    }
}
