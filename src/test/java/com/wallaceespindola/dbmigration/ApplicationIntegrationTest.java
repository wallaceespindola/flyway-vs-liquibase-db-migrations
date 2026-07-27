package com.wallaceespindola.dbmigration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.AppliedMigration;
import com.wallaceespindola.dbmigration.dto.ComparisonReport;
import com.wallaceespindola.dbmigration.dto.ProductView;
import com.wallaceespindola.dbmigration.service.ComparisonService;
import com.wallaceespindola.dbmigration.service.SchemaInspectionService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end test against two real, freshly migrated in-memory H2 databases.
 *
 * <p>This is the test that matters most: it boots the whole application, lets Flyway and Liquibase
 * each migrate their own database for real, and then asserts on what they actually produced. The
 * central claim of the project — that both engines converge on the same business schema — is
 * verified here rather than asserted in prose.
 *
 * @author Wallace Espindola
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComparisonService comparisonService;

    @Autowired
    private SchemaInspectionService schemaInspectionService;

    @Nested
    @DisplayName("the central claim")
    class CentralClaim {

        @Test
        @DisplayName("both engines produce an equivalent business schema")
        void bothEnginesProduceEquivalentSchema() {
            ComparisonReport report = comparisonService.compare();

            assertThat(report.schemaDifferences())
                    .as("Flyway and Liquibase should converge on the same business schema")
                    .isEmpty();
            assertThat(report.schemasEquivalent()).isTrue();
        }

        @Test
        @DisplayName("both engines created the same tables and views")
        void bothEnginesCreatedTheSameObjects() {
            var flyway = schemaInspectionService.snapshot(MigrationEngine.FLYWAY);
            var liquibase = schemaInspectionService.snapshot(MigrationEngine.LIQUIBASE);

            assertThat(flyway.tables()).containsExactlyInAnyOrder("CATEGORY", "PRODUCT", "PRODUCT_AUDIT");
            assertThat(liquibase.tables()).containsExactlyInAnyOrderElementsOf(flyway.tables());
            assertThat(flyway.views()).containsExactly("V_PRODUCT_CATALOG");
            assertThat(liquibase.views()).containsExactlyInAnyOrderElementsOf(flyway.views());
            assertThat(liquibase.columns()).containsExactlyInAnyOrderElementsOf(flyway.columns());
        }

        @Test
        @DisplayName("only the engine bookkeeping tables differ between the two databases")
        void onlyBookkeepingDiffers() {
            var flyway = schemaInspectionService.snapshot(MigrationEngine.FLYWAY);
            var liquibase = schemaInspectionService.snapshot(MigrationEngine.LIQUIBASE);

            assertThat(flyway.bookkeepingTables()).containsExactly("flyway_schema_history");
            assertThat(liquibase.bookkeepingTables())
                    .containsExactlyInAnyOrder("DATABASECHANGELOG", "DATABASECHANGELOGLOCK");
        }

        @Test
        @DisplayName("both engines seeded identical business data")
        void bothEnginesSeededIdenticalData() {
            List<ProductView> flyway = schemaInspectionService.catalog(MigrationEngine.FLYWAY);
            List<ProductView> liquibase = schemaInspectionService.catalog(MigrationEngine.LIQUIBASE);

            assertThat(flyway).hasSize(5);
            assertThat(liquibase).containsExactlyElementsOf(flyway);
        }
    }

    @Nested
    @DisplayName("Flyway migration history")
    class FlywayHistory {

        @Test
        @DisplayName("applies every versioned migration plus the repeatable one")
        void appliesAllMigrations() {
            var status = comparisonService.status(MigrationEngine.FLYWAY);

            assertThat(status.appliedCount()).isEqualTo(6);
            assertThat(status.pendingCount()).isZero();
            assertThat(status.upToDate()).isTrue();
            assertThat(status.lastAppliedAt()).isNotNull();
            assertThat(status.migrations())
                    .extracting(AppliedMigration::identifier)
                    .containsExactly("V1", "V2", "V3", "V4", "V5", "R__product catalog view");
        }

        @Test
        @DisplayName("records per-migration execution time — a capability Liquibase lacks")
        void recordsExecutionTime() {
            assertThat(comparisonService.status(MigrationEngine.FLYWAY).migrations())
                    .allSatisfy(m -> assertThat(m.executionTimeMs()).isNotNull());
        }

        @Test
        @DisplayName("records no author, because Flyway does not track one")
        void recordsNoAuthor() {
            assertThat(comparisonService.status(MigrationEngine.FLYWAY).migrations())
                    .extracting(AppliedMigration::author)
                    .containsOnly("n/a");
        }
    }

    @Nested
    @DisplayName("Liquibase changelog history")
    class LiquibaseHistory {

        @Test
        @DisplayName("applies every changeset from the master changelog")
        void appliesAllChangesets() {
            var status = comparisonService.status(MigrationEngine.LIQUIBASE);

            assertThat(status.appliedCount()).isEqualTo(7);
            assertThat(status.upToDate()).isTrue();
            assertThat(status.migrations())
                    .extracting(AppliedMigration::identifier)
                    .containsExactly(
                            "001-create-category-table::wallaceespindola",
                            "002-create-product-table::wallaceespindola",
                            "003-seed-reference-data::wallaceespindola",
                            "004-add-product-audit-table::wallaceespindola",
                            "005-add-product-active-flag::wallaceespindola",
                            "005b-backfill-product-active-flag::wallaceespindola",
                            "006-product-catalog-view::wallaceespindola");
        }

        @Test
        @DisplayName("applies changesets written in XML, YAML and SQL alike")
        void appliesEveryChangelogFormat() {
            List<String> scripts = comparisonService.status(MigrationEngine.LIQUIBASE).migrations().stream()
                    .map(AppliedMigration::script)
                    .toList();

            assertThat(scripts).anyMatch(s -> s.endsWith(".xml"));
            assertThat(scripts).anyMatch(s -> s.endsWith(".yaml"));
            assertThat(scripts).anyMatch(s -> s.endsWith(".sql"));
        }

        @Test
        @DisplayName("records an author for every changeset — a capability Flyway lacks")
        void recordsAuthor() {
            assertThat(comparisonService.status(MigrationEngine.LIQUIBASE).migrations())
                    .extracting(AppliedMigration::author)
                    .containsOnly("wallaceespindola");
        }

        @Test
        @DisplayName("records no execution time, unlike Flyway")
        void recordsNoExecutionTime() {
            assertThat(comparisonService.status(MigrationEngine.LIQUIBASE).migrations())
                    .allSatisfy(m -> assertThat(m.executionTimeMs()).isNull());
        }
    }

    @Nested
    @DisplayName("migration data the scripts produced")
    class MigratedData {

        @ParameterizedTest
        @ValueSource(strings = {"FLYWAY", "LIQUIBASE"})
        @DisplayName("the audit backfill from migration 004 ran on both sides")
        void auditBackfillRan(String engineName) {
            MigrationEngine engine = MigrationEngine.from(engineName);
            assertThat(schemaInspectionService.snapshot(engine).tables()).contains("PRODUCT_AUDIT");
        }

        @ParameterizedTest
        @ValueSource(strings = {"FLYWAY", "LIQUIBASE"})
        @DisplayName("the active flag added in migration 005 is present and populated")
        void activeFlagPresent(String engineName) {
            MigrationEngine engine = MigrationEngine.from(engineName);

            assertThat(schemaInspectionService.snapshot(engine).columns())
                    .anyMatch(column -> column.startsWith("PRODUCT.ACTIVE:"));
            assertThat(schemaInspectionService.catalog(engine))
                    .allSatisfy(product -> assertThat(product.active()).isNotNull());
        }
    }

    @Nested
    @DisplayName("REST API")
    class RestApi {

        @Test
        @DisplayName("GET /api/v1/comparison returns the full report")
        void comparisonEndpoint() throws Exception {
            mockMvc.perform(get("/api/v1/comparison"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                    .andExpect(jsonPath("$.data.schemasEquivalent").value(true))
                    .andExpect(jsonPath("$.data.schemaDifferences", hasSize(0)))
                    .andExpect(jsonPath("$.data.flyway.appliedCount").value(6))
                    .andExpect(jsonPath("$.data.liquibase.appliedCount").value(7))
                    .andExpect(jsonPath("$.data.featureMatrix", hasSize(greaterThanOrEqualTo(15))));
        }

        @Test
        @DisplayName("GET /api/v1/comparison/features returns a balanced matrix")
        void featuresEndpoint() throws Exception {
            mockMvc.perform(get("/api/v1/comparison/features"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].edge", everyItem(notNullValue())))
                    .andExpect(jsonPath("$.data[*].edge", hasItem("FLYWAY")))
                    .andExpect(jsonPath("$.data[*].edge", hasItem("LIQUIBASE")))
                    .andExpect(jsonPath("$.data[*].edge", hasItem("TIE")));
        }

        @Test
        @DisplayName("GET /api/v1/migrations returns both engines")
        void bothEnginesEndpoint() throws Exception {
            mockMvc.perform(get("/api/v1/migrations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.flyway.appliedCount").value(6))
                    .andExpect(jsonPath("$.data.liquibase.appliedCount").value(7));
        }

        @Test
        @DisplayName("GET /api/v1/migrations/engines describes both engines")
        void enginesEndpoint() throws Exception {
            mockMvc.perform(get("/api/v1/migrations/engines"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[*].id", hasItem("flyway")))
                    .andExpect(jsonPath("$.data[*].id", hasItem("liquibase")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"flyway", "FLYWAY", "liquibase", "LiquiBase"})
        @DisplayName("GET /api/v1/migrations/{engine} accepts any casing")
        void perEngineEndpoint(String engine) throws Exception {
            mockMvc.perform(get("/api/v1/migrations/{engine}", engine))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.appliedCount", greaterThanOrEqualTo(6)))
                    .andExpect(jsonPath("$.data.migrations", hasSize(greaterThanOrEqualTo(6))));
        }

        @ParameterizedTest
        @ValueSource(strings = {"flyway", "liquibase"})
        @DisplayName("GET /api/v1/migrations/{engine}/schema lists the produced objects")
        void schemaEndpoint(String engine) throws Exception {
            mockMvc.perform(get("/api/v1/migrations/{engine}/schema", engine))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tables", hasSize(3)))
                    .andExpect(jsonPath("$.data.tables", hasItem("PRODUCT")))
                    .andExpect(jsonPath("$.data.views", hasItem("V_PRODUCT_CATALOG")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"flyway", "liquibase"})
        @DisplayName("GET /api/v1/catalog/{engine} returns the seeded products")
        void catalogEndpoint(String engine) throws Exception {
            mockMvc.perform(get("/api/v1/catalog/{engine}", engine))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(5)))
                    .andExpect(jsonPath("$.data[*].sku", hasItem("DT-FW-010")))
                    .andExpect(jsonPath("$.data[*].sku", hasItem("DT-LB-011")))
                    .andExpect(jsonPath("$.data[0].categoryName", notNullValue()));
        }

        @Test
        @DisplayName("GET /api/v1/health reports both databases UP")
        void healthEndpoint() throws Exception {
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("UP"))
                    .andExpect(jsonPath("$.data.flyway.status").value("UP"))
                    .andExpect(jsonPath("$.data.liquibase.status").value("UP"));
        }

        @Test
        @DisplayName("an unknown engine is rejected with 400 and a helpful message")
        void unknownEngineIsRejected() throws Exception {
            mockMvc.perform(get("/api/v1/migrations/{engine}", "mongodb"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                    .andExpect(jsonPath("$.message", containsString("Unknown migration engine")));
        }

        @Test
        @DisplayName("an unknown engine on the catalog endpoint is rejected too")
        void unknownEngineOnCatalogIsRejected() throws Exception {
            mockMvc.perform(get("/api/v1/catalog/{engine}", "sqlite"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("static frontend")
    class Frontend {

        @Test
        @DisplayName("the application root forwards to the dashboard")
        void rootForwardsToDashboard() throws Exception {
            // Spring Boot serves the welcome page by forwarding to index.html. MockMvc records the
            // forward but does not execute it, so the body is asserted on index.html directly below.
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("index.html"));
        }

        @Test
        @DisplayName("the dashboard renders both engines and wires up its own assets")
        void dashboardIsServed() throws Exception {
            mockMvc.perform(get("/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Flyway")))
                    .andExpect(content().string(containsString("Liquibase")))
                    .andExpect(content().string(containsString("css/styles.css")))
                    .andExpect(content().string(containsString("js/app.js")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/css/styles.css", "/js/app.js"})
        @DisplayName("dashboard assets are served")
        void assetsAreServed(String path) throws Exception {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }
}
