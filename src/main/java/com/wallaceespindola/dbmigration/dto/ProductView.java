package com.wallaceespindola.dbmigration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * A row of {@code v_product_catalog} — the business data that proves the migrations actually ran and
 * produced a queryable schema on both sides.
 *
 * @author Wallace Espindola
 */
@Schema(description = "A product catalog row read from the v_product_catalog view")
public record ProductView(
        Long productId,
        String sku,
        String productName,
        BigDecimal price,
        Integer stockQuantity,
        Boolean active,
        Long categoryId,
        String categoryName) {}
