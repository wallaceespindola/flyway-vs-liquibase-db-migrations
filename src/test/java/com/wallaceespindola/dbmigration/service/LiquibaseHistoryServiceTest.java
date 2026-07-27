package com.wallaceespindola.dbmigration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.AppliedMigration;
import com.wallaceespindola.dbmigration.dto.MigrationStatusReport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Verifies how {@code DATABASECHANGELOG} rows are normalised into {@link AppliedMigration}. The row
 * mapper is captured from the {@link JdbcTemplate} call and driven against a stub result set, so the
 * mapping itself is under test rather than mocked away.
 *
 * @author Wallace Espindola
 */
class LiquibaseHistoryServiceTest {

    private JdbcTemplate jdbcTemplate;
    private LiquibaseHistoryService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new LiquibaseHistoryService(jdbcTemplate);
    }

    @Test
    @DisplayName("reports the Liquibase engine")
    void reportsEngine() {
        assertThat(service.engine()).isEqualTo(MigrationEngine.LIQUIBASE);
    }

    @Test
    @DisplayName("queries DATABASECHANGELOG ordered by execution order")
    void queriesChangelogInOrder() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AppliedMigration>>any()))
                .thenReturn(List.of());

        service.status();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate)
                .query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<AppliedMigration>>any());

        assertThat(sql.getValue()).contains("DATABASECHANGELOG").contains("ORDER BY ORDEREXECUTED");
    }

    @Test
    @DisplayName("summarises an empty changelog without failing")
    void handlesEmptyChangelog() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AppliedMigration>>any()))
                .thenReturn(List.of());

        MigrationStatusReport report = service.status();

        assertThat(report.appliedCount()).isZero();
        assertThat(report.lastAppliedAt()).isNull();
        assertThat(report.upToDate()).isTrue();
        assertThat(report.historyTable()).isEqualTo("DATABASECHANGELOG");
        assertThat(report.engineDisplayName()).isEqualTo("Liquibase");
    }

    @Test
    @DisplayName("summarises applied changesets and takes the last timestamp")
    void summarisesAppliedChangesets() {
        Instant earlier = Instant.parse("2026-01-01T10:00:00Z");
        Instant later = Instant.parse("2026-01-01T10:05:00Z");

        List<AppliedMigration> rows = List.of(
                new AppliedMigration("001::wallace", "first", "001.xml", "EXECUTED", "wallace", "md5a", earlier, null,
                        "EXECUTED"),
                new AppliedMigration("002::wallace", "second", "002.yaml", "EXECUTED", "wallace", "md5b", later, null,
                        "EXECUTED"));

        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AppliedMigration>>any()))
                .thenReturn(rows);

        MigrationStatusReport report = service.status();

        assertThat(report.appliedCount()).isEqualTo(2);
        assertThat(report.pendingCount()).isZero();
        assertThat(report.lastAppliedAt()).isEqualTo(later);
        assertThat(report.migrations()).containsExactlyElementsOf(rows);
    }

    @Test
    @DisplayName("maps a changelog row into the normalised shape")
    void mapsChangelogRow() throws SQLException {
        RowMapper<AppliedMigration> mapper = captureRowMapper();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ID")).thenReturn("004-add-product-audit-table");
        when(rs.getString("AUTHOR")).thenReturn("wallaceespindola");
        when(rs.getString("FILENAME")).thenReturn("db/changelog/changes/004-add-product-audit-table.xml");
        when(rs.getString("EXECTYPE")).thenReturn("EXECUTED");
        when(rs.getString("MD5SUM")).thenReturn("9:abc123");
        when(rs.getString("COMMENTS")).thenReturn("Add the product audit trail");
        when(rs.getTimestamp("DATEEXECUTED")).thenReturn(Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")));

        AppliedMigration migration = mapper.mapRow(rs, 0);

        assertThat(migration.identifier()).isEqualTo("004-add-product-audit-table::wallaceespindola");
        assertThat(migration.author()).isEqualTo("wallaceespindola");
        assertThat(migration.description()).isEqualTo("Add the product audit trail");
        assertThat(migration.script()).endsWith("004-add-product-audit-table.xml");
        assertThat(migration.checksum()).isEqualTo("9:abc123");
        assertThat(migration.appliedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(migration.status()).isEqualTo("EXECUTED");
    }

    @Test
    @DisplayName("falls back to DESCRIPTION when the changeset has no comment")
    void fallsBackToDescription() throws SQLException {
        RowMapper<AppliedMigration> mapper = captureRowMapper();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ID")).thenReturn("001");
        when(rs.getString("AUTHOR")).thenReturn("wallace");
        when(rs.getString("COMMENTS")).thenReturn("   ");
        when(rs.getString("DESCRIPTION")).thenReturn("createTable tableName=category");
        when(rs.getTimestamp("DATEEXECUTED")).thenReturn(Timestamp.from(Instant.now()));

        assertThat(mapper.mapRow(rs, 0).description()).isEqualTo("createTable tableName=category");
    }

    @Test
    @DisplayName("records no execution time, because Liquibase does not persist one")
    void recordsNoExecutionTime() throws SQLException {
        RowMapper<AppliedMigration> mapper = captureRowMapper();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ID")).thenReturn("001");
        when(rs.getString("AUTHOR")).thenReturn("wallace");
        when(rs.getString("COMMENTS")).thenReturn("anything");
        when(rs.getTimestamp("DATEEXECUTED")).thenReturn(Timestamp.from(Instant.now()));

        assertThat(mapper.mapRow(rs, 0).executionTimeMs()).isNull();
    }

    @Test
    @DisplayName("tolerates a null DATEEXECUTED")
    void tolerantOfNullTimestamp() throws SQLException {
        RowMapper<AppliedMigration> mapper = captureRowMapper();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ID")).thenReturn("001");
        when(rs.getString("AUTHOR")).thenReturn("wallace");
        when(rs.getString("COMMENTS")).thenReturn("anything");
        when(rs.getTimestamp("DATEEXECUTED")).thenReturn(null);

        assertThat(mapper.mapRow(rs, 0).appliedAt()).isNull();
    }

    /** Runs status() purely to capture the row mapper the service hands to the JdbcTemplate. */
    private RowMapper<AppliedMigration> captureRowMapper() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AppliedMigration>>any()))
                .thenReturn(List.of());
        service.status();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<AppliedMigration>> captor = ArgumentCaptor.forClass(RowMapper.class);
        org.mockito.Mockito.verify(jdbcTemplate).query(anyString(), captor.capture());
        return captor.getValue();
    }
}
