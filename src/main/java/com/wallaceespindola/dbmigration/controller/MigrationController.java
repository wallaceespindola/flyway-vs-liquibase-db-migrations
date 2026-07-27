package com.wallaceespindola.dbmigration.controller;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.ApiResponse;
import com.wallaceespindola.dbmigration.dto.MigrationStatusReport;
import com.wallaceespindola.dbmigration.dto.SchemaSnapshot;
import com.wallaceespindola.dbmigration.service.ComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Migration status endpoints, per engine and for both at once.
 *
 * @author Wallace Espindola
 */
@RestController
@RequestMapping("/api/v1/migrations")
@RequiredArgsConstructor
@Tag(name = "Migrations", description = "Applied-migration history for Flyway and Liquibase")
public class MigrationController {

    private final ComparisonService comparisonService;

    @GetMapping
    @Operation(summary = "Migration status for both engines, keyed by engine name")
    public ResponseEntity<ApiResponse<Map<String, MigrationStatusReport>>> statusForBothEngines() {
        Map<String, MigrationStatusReport> byEngine = Map.of(
                MigrationEngine.FLYWAY.name().toLowerCase(), comparisonService.status(MigrationEngine.FLYWAY),
                MigrationEngine.LIQUIBASE.name().toLowerCase(), comparisonService.status(MigrationEngine.LIQUIBASE));
        return ResponseEntity.ok(ApiResponse.ok(byEngine, "Migration status for both engines"));
    }

    @GetMapping("/engines")
    @Operation(summary = "The engines available for comparison")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> engines() {
        List<Map<String, String>> engines = List.of(MigrationEngine.values()).stream()
                .map(engine -> Map.of(
                        "id", engine.name().toLowerCase(),
                        "displayName", engine.displayName(),
                        "historyTable", engine.historyTable(),
                        "model", engine.model()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(engines, "Available migration engines"));
    }

    @GetMapping("/{engine}")
    @Operation(summary = "Migration status for one engine")
    public ResponseEntity<ApiResponse<MigrationStatusReport>> status(
            @Parameter(description = "flyway or liquibase", example = "flyway") @PathVariable String engine) {
        MigrationEngine resolved = MigrationEngine.from(engine);
        MigrationStatusReport report = comparisonService.status(resolved);
        return ResponseEntity.ok(
                ApiResponse.ok(report, "%s applied %d migrations".formatted(resolved.displayName(),
                        report.appliedCount())));
    }

    @GetMapping("/{engine}/schema")
    @Operation(summary = "Schema objects one engine produced, read from INFORMATION_SCHEMA")
    public ResponseEntity<ApiResponse<SchemaSnapshot>> schema(
            @Parameter(description = "flyway or liquibase", example = "liquibase") @PathVariable String engine) {
        MigrationEngine resolved = MigrationEngine.from(engine);
        SchemaSnapshot snapshot = comparisonService.schema(resolved);
        return ResponseEntity.ok(
                ApiResponse.ok(snapshot, "%s schema contains %d business tables".formatted(
                        resolved.displayName(), snapshot.tables().size())));
    }
}
