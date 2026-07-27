package com.wallaceespindola.dbmigration.controller;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.ApiResponse;
import com.wallaceespindola.dbmigration.dto.ProductView;
import com.wallaceespindola.dbmigration.service.SchemaInspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Business data endpoints — proof that each engine's migrations produced a working, queryable
 * schema and not just bookkeeping rows.
 *
 * @author Wallace Espindola
 */
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Product data seeded by each engine's migrations")
public class CatalogController {

    private final SchemaInspectionService schemaInspectionService;

    @GetMapping("/{engine}")
    @Operation(summary = "Product catalog from one engine's database, read through v_product_catalog")
    public ResponseEntity<ApiResponse<List<ProductView>>> catalog(
            @Parameter(description = "flyway or liquibase", example = "flyway") @PathVariable String engine) {
        MigrationEngine resolved = MigrationEngine.from(engine);
        List<ProductView> products = schemaInspectionService.catalog(resolved);
        return ResponseEntity.ok(ApiResponse.ok(
                products, "%d products in the %s database".formatted(products.size(), resolved.displayName())));
    }
}
