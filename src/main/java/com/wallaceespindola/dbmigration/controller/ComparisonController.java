package com.wallaceespindola.dbmigration.controller;

import com.wallaceespindola.dbmigration.dto.ApiResponse;
import com.wallaceespindola.dbmigration.dto.ComparisonReport;
import com.wallaceespindola.dbmigration.dto.FeatureComparison;
import com.wallaceespindola.dbmigration.service.ComparisonService;
import com.wallaceespindola.dbmigration.service.FeatureMatrix;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The headline endpoints: the full side-by-side comparison and the standalone feature matrix.
 *
 * @author Wallace Espindola
 */
@RestController
@RequestMapping("/api/v1/comparison")
@RequiredArgsConstructor
@Tag(name = "Comparison", description = "Side-by-side comparison of Flyway and Liquibase")
public class ComparisonController {

    private final ComparisonService comparisonService;

    @GetMapping
    @Operation(summary = "Full comparison: both engines' status, both schemas, structural diff and feature matrix")
    public ResponseEntity<ApiResponse<ComparisonReport>> compare() {
        ComparisonReport report = comparisonService.compare();
        String message = report.schemasEquivalent()
                ? "Both engines produced an equivalent business schema"
                : "Schemas differ in %d place(s)".formatted(report.schemaDifferences().size());
        return ResponseEntity.ok(ApiResponse.ok(report, message));
    }

    @GetMapping("/features")
    @Operation(summary = "Capability-by-capability feature matrix")
    public ResponseEntity<ApiResponse<List<FeatureComparison>>> features() {
        return ResponseEntity.ok(
                ApiResponse.ok(FeatureMatrix.rows(), "%d features compared".formatted(FeatureMatrix.rows().size())));
    }
}
