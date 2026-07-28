package com.wallaceespindola.dbmigration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.ApiResponse;
import com.wallaceespindola.dbmigration.dto.MigrationStatusReport;
import com.wallaceespindola.dbmigration.service.ComparisonService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The healthy path is covered end to end in {@code ApplicationIntegrationTest}. These tests cover
 * the degraded paths, which cannot be triggered against a working database.
 *
 * @author Wallace Espindola
 */
class HealthControllerTest {

    private ComparisonService comparisonService;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        comparisonService = mock(ComparisonService.class);
        controller = new HealthController(comparisonService);
    }

    private static MigrationStatusReport report(MigrationEngine engine, int applied) {
        return new MigrationStatusReport(
                engine, engine.displayName(), engine.historyTable(), applied, 0, Instant.now(), true, List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> engineDetails(ApiResponse<Map<String, Object>> body, String engine) {
        return (Map<String, Object>) body.data().get(engine);
    }

    @Test
    @DisplayName("reports UP with per-engine migration counts when both databases answer")
    void reportsUpWhenBothEnginesAnswer() {
        when(comparisonService.status(MigrationEngine.FLYWAY)).thenReturn(report(MigrationEngine.FLYWAY, 6));
        when(comparisonService.status(MigrationEngine.LIQUIBASE)).thenReturn(report(MigrationEngine.LIQUIBASE, 7));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().get("status")).isEqualTo("UP");
        assertThat(engineDetails(response.getBody(), "flyway"))
                .containsEntry("status", "UP")
                .containsEntry("appliedMigrations", 6);
        assertThat(engineDetails(response.getBody(), "liquibase")).containsEntry("appliedMigrations", 7);
    }

    @Test
    @DisplayName("reports 503 when one engine's database is unreachable, and still reports the healthy one")
    void reportsDownWhenOneEngineFails() {
        when(comparisonService.status(MigrationEngine.FLYWAY)).thenReturn(report(MigrationEngine.FLYWAY, 6));
        when(comparisonService.status(MigrationEngine.LIQUIBASE))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data().get("status")).isEqualTo("DOWN");
        assertThat(engineDetails(response.getBody(), "flyway")).containsEntry("status", "UP");
        assertThat(engineDetails(response.getBody(), "liquibase"))
                .containsEntry("status", "DOWN")
                .hasEntrySatisfying("error", value -> assertThat(String.valueOf(value)).contains("connection refused"));
    }

    @Test
    @DisplayName("reports 503 when both databases are unreachable")
    void reportsDownWhenBothEnginesFail() {
        when(comparisonService.status(MigrationEngine.FLYWAY))
                .thenThrow(new DataAccessResourceFailureException("flyway pool exhausted"));
        when(comparisonService.status(MigrationEngine.LIQUIBASE))
                .thenThrow(new DataAccessResourceFailureException("liquibase pool exhausted"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().get("status")).isEqualTo("DOWN");
        assertThat(engineDetails(response.getBody(), "flyway")).containsEntry("status", "DOWN");
        assertThat(engineDetails(response.getBody(), "liquibase")).containsEntry("status", "DOWN");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("a null exception message does not produce a null-literal crash")
    void tolerantOfNullExceptionMessage() {
        when(comparisonService.status(MigrationEngine.FLYWAY)).thenReturn(report(MigrationEngine.FLYWAY, 6));
        when(comparisonService.status(MigrationEngine.LIQUIBASE)).thenThrow(new IllegalStateException());

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(engineDetails(response.getBody(), "liquibase")).containsEntry("error", "null");
    }
}
