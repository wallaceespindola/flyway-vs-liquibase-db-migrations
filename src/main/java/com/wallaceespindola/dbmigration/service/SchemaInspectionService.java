package com.wallaceespindola.dbmigration.service;

import com.wallaceespindola.dbmigration.config.FlywayConfig;
import com.wallaceespindola.dbmigration.config.LiquibaseConfig;
import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.ProductView;
import com.wallaceespindola.dbmigration.dto.SchemaSnapshot;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Reads back what each engine actually built, and the business data it seeded.
 *
 * <p>Everything here goes through {@code INFORMATION_SCHEMA} rather than trusting the migration
 * scripts, so the comparison reflects the real database state on both sides.
 *
 * @author Wallace Espindola
 */
@Slf4j
@Service
public class SchemaInspectionService {

    /** Tables the engines maintain for themselves; excluded from the business-schema comparison. */
    private static final Set<String> BOOKKEEPING_TABLES =
            Set.of("FLYWAY_SCHEMA_HISTORY", "DATABASECHANGELOG", "DATABASECHANGELOGLOCK");

    private static final String SCHEMA = "PUBLIC";

    private static final String TABLES_QUERY =
            """
            SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_NAME
            """;

    private static final String VIEWS_QUERY =
            """
            SELECT TABLE_NAME FROM INFORMATION_SCHEMA.VIEWS
            WHERE TABLE_SCHEMA = ?
            ORDER BY TABLE_NAME
            """;

    private static final String COLUMNS_QUERY =
            """
            SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = ?
            ORDER BY TABLE_NAME, COLUMN_NAME
            """;

    private static final String INDEXES_QUERY =
            """
            SELECT DISTINCT INDEX_NAME, TABLE_NAME FROM INFORMATION_SCHEMA.INDEXES
            WHERE INDEX_SCHEMA = ?
            ORDER BY TABLE_NAME, INDEX_NAME
            """;

    private static final String CATALOG_QUERY =
            """
            SELECT product_id, sku, product_name, price, stock_quantity, active, category_id, category_name
            FROM v_product_catalog
            ORDER BY sku
            """;

    private static final RowMapper<ProductView> PRODUCT_ROW_MAPPER = (rs, rowNum) -> new ProductView(
            rs.getLong("product_id"),
            rs.getString("sku"),
            rs.getString("product_name"),
            rs.getBigDecimal("price"),
            rs.getInt("stock_quantity"),
            rs.getBoolean("active"),
            rs.getLong("category_id"),
            rs.getString("category_name"));

    private final Map<MigrationEngine, JdbcTemplate> templates;

    public SchemaInspectionService(
            @Qualifier(FlywayConfig.JDBC_TEMPLATE) JdbcTemplate flywayJdbcTemplate,
            @Qualifier(LiquibaseConfig.JDBC_TEMPLATE) JdbcTemplate liquibaseJdbcTemplate) {
        this.templates = Map.of(
                MigrationEngine.FLYWAY, flywayJdbcTemplate,
                MigrationEngine.LIQUIBASE, liquibaseJdbcTemplate);
    }

    /** Reads the current schema objects for one engine's database. */
    public SchemaSnapshot snapshot(MigrationEngine engine) {
        JdbcTemplate jdbc = templateFor(engine);

        List<String> allTables = jdbc.queryForList(TABLES_QUERY, String.class, SCHEMA);
        List<String> businessTables = allTables.stream().filter(t -> !isBookkeeping(t)).toList();
        List<String> bookkeeping = allTables.stream().filter(SchemaInspectionService::isBookkeeping).toList();

        List<String> views = jdbc.queryForList(VIEWS_QUERY, String.class, SCHEMA);

        List<String> columns = jdbc.query(
                        COLUMNS_QUERY,
                        (rs, rowNum) -> "%s.%s:%s".formatted(
                                rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME"), rs.getString("DATA_TYPE")),
                        SCHEMA)
                .stream()
                .filter(entry -> !isBookkeeping(entry.substring(0, entry.indexOf('.'))))
                .toList();

        List<String> indexes = jdbc.query(
                        INDEXES_QUERY,
                        (rs, rowNum) -> "%s.%s".formatted(rs.getString("TABLE_NAME"), rs.getString("INDEX_NAME")),
                        SCHEMA)
                .stream()
                .filter(entry -> !isBookkeeping(entry.substring(0, entry.indexOf('.'))))
                .toList();

        log.debug("{} schema: {} tables, {} views, {} columns", engine, businessTables.size(), views.size(),
                columns.size());

        return new SchemaSnapshot(engine, businessTables, views, columns, indexes, bookkeeping);
    }

    /** Reads the seeded product catalog from one engine's database. */
    public List<ProductView> catalog(MigrationEngine engine) {
        return templateFor(engine).query(CATALOG_QUERY, PRODUCT_ROW_MAPPER);
    }

    private JdbcTemplate templateFor(MigrationEngine engine) {
        return templates.get(engine);
    }

    private static boolean isBookkeeping(String tableName) {
        return BOOKKEEPING_TABLES.contains(tableName.toUpperCase(Locale.ROOT));
    }
}
