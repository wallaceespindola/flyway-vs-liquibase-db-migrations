package com.wallaceespindola.dbmigration.controller;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.ApiResponse;
import com.wallaceespindola.dbmigration.service.ComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Application health, expressed in terms this application actually cares about: can both migrated
 * databases be queried?
 *
 * <p>Complements Actuator's {@code /actuator/health} rather than replacing it.
 *
 * @author Wallace Espindola
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Liveness of both migrated databases")
public class HealthController {

    private final ComparisonService comparisonService;

    @GetMapping
    @Operation(summary = "Reports UP only when both engines' databases answer a status query")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean healthy = true;

        for (MigrationEngine engine : MigrationEngine.values()) {
            try {
                int applied = comparisonService.status(engine).appliedCount();
                details.put(engine.name().toLowerCase(), Map.of("status", "UP", "appliedMigrations", applied));
            } catch (RuntimeException e) {
                log.error("Health check failed for {}", engine, e);
                healthy = false;
                details.put(engine.name().toLowerCase(), Map.of("status", "DOWN", "error", String.valueOf(e.getMessage())));
            }
        }

        details.put("status", healthy ? "UP" : "DOWN");

        return healthy
                ? ResponseEntity.ok(ApiResponse.ok(details, "All migrated databases are reachable"))
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ApiResponse<>(false, "One or more databases are unreachable", details,
                                java.time.Instant.now()));
    }
}
