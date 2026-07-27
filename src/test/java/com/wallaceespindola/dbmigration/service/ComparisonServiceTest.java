package com.wallaceespindola.dbmigration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.ComparisonReport;
import com.wallaceespindola.dbmigration.dto.MigrationStatusReport;
import com.wallaceespindola.dbmigration.dto.SchemaSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the schema-diff logic and engine dispatch. The engines themselves are exercised
 * against real databases in {@code ApplicationIntegrationTest}.
 *
 * @author Wallace Espindola
 */
class ComparisonServiceTest {

    private static SchemaSnapshot snapshot(
            MigrationEngine engine, List<String> tables, List<String> views, List<String> columns) {
        return new SchemaSnapshot(engine, tables, views, columns, List.of(), List.of());
    }

    private static MigrationStatusReport status(MigrationEngine engine, int applied) {
        return new MigrationStatusReport(
                engine, engine.displayName(), engine.historyTable(), applied, 0, Instant.now(), true, List.of());
    }

    @Nested
    @DisplayName("schema diff")
    class Diff {

        @Test
        @DisplayName("reports no differences for identical schemas")
        void identicalSchemasHaveNoDifferences() {
            SchemaSnapshot flyway = snapshot(
                    MigrationEngine.FLYWAY, List.of("PRODUCT"), List.of("V_CATALOG"), List.of("PRODUCT.ID:BIGINT"));
            SchemaSnapshot liquibase = snapshot(
                    MigrationEngine.LIQUIBASE, List.of("PRODUCT"), List.of("V_CATALOG"), List.of("PRODUCT.ID:BIGINT"));

            assertThat(ComparisonService.diff(flyway, liquibase)).isEmpty();
        }

        @Test
        @DisplayName("ignores ordering differences")
        void orderingDoesNotMatter() {
            SchemaSnapshot flyway =
                    snapshot(MigrationEngine.FLYWAY, List.of("A", "B"), List.of(), List.of("A.X:INT", "B.Y:INT"));
            SchemaSnapshot liquibase =
                    snapshot(MigrationEngine.LIQUIBASE, List.of("B", "A"), List.of(), List.of("B.Y:INT", "A.X:INT"));

            assertThat(ComparisonService.diff(flyway, liquibase)).isEmpty();
        }

        @Test
        @DisplayName("reports a table only Flyway created")
        void reportsFlywayOnlyTable() {
            SchemaSnapshot flyway = snapshot(MigrationEngine.FLYWAY, List.of("PRODUCT", "EXTRA"), List.of(), List.of());
            SchemaSnapshot liquibase = snapshot(MigrationEngine.LIQUIBASE, List.of("PRODUCT"), List.of(), List.of());

            assertThat(ComparisonService.diff(flyway, liquibase))
                    .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .contains("table 'EXTRA'")
                    .contains("Flyway schema but not in the Liquibase schema");
        }

        @Test
        @DisplayName("reports a view only Liquibase created")
        void reportsLiquibaseOnlyView() {
            SchemaSnapshot flyway = snapshot(MigrationEngine.FLYWAY, List.of(), List.of(), List.of());
            SchemaSnapshot liquibase = snapshot(MigrationEngine.LIQUIBASE, List.of(), List.of("V_ONLY"), List.of());

            assertThat(ComparisonService.diff(flyway, liquibase))
                    .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .contains("view 'V_ONLY'")
                    .contains("Liquibase schema but not in the Flyway schema");
        }

        @Test
        @DisplayName("catches a column type mismatch between the two engines")
        void catchesColumnTypeMismatch() {
            SchemaSnapshot flyway = snapshot(
                    MigrationEngine.FLYWAY, List.of("PRODUCT"), List.of(), List.of("PRODUCT.PRICE:NUMERIC"));
            SchemaSnapshot liquibase = snapshot(
                    MigrationEngine.LIQUIBASE, List.of("PRODUCT"), List.of(), List.of("PRODUCT.PRICE:DOUBLE"));

            assertThat(ComparisonService.diff(flyway, liquibase))
                    .hasSize(2)
                    .anySatisfy(d -> assertThat(d).contains("PRODUCT.PRICE:NUMERIC"))
                    .anySatisfy(d -> assertThat(d).contains("PRODUCT.PRICE:DOUBLE"));
        }

        @Test
        @DisplayName("reports differences from both directions at once")
        void reportsBothDirections() {
            SchemaSnapshot flyway = snapshot(MigrationEngine.FLYWAY, List.of("ONLY_FW"), List.of(), List.of());
            SchemaSnapshot liquibase = snapshot(MigrationEngine.LIQUIBASE, List.of("ONLY_LB"), List.of(), List.of());

            assertThat(ComparisonService.diff(flyway, liquibase)).hasSize(2);
        }

        @Test
        @DisplayName("the difference list is immutable")
        void differenceListIsImmutable() {
            SchemaSnapshot flyway = snapshot(MigrationEngine.FLYWAY, List.of("A"), List.of(), List.of());
            SchemaSnapshot liquibase = snapshot(MigrationEngine.LIQUIBASE, List.of(), List.of(), List.of());

            List<String> differences = ComparisonService.diff(flyway, liquibase);
            assertThatThrownBy(() -> differences.add("x")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("engine dispatch")
    class Dispatch {

        private MigrationHistoryProvider flywayProvider;
        private MigrationHistoryProvider liquibaseProvider;
        private SchemaInspectionService schemaInspection;
        private ComparisonService service;

        @BeforeEach
        void setUp() {
            flywayProvider = mock(MigrationHistoryProvider.class);
            liquibaseProvider = mock(MigrationHistoryProvider.class);
            schemaInspection = mock(SchemaInspectionService.class);

            when(flywayProvider.engine()).thenReturn(MigrationEngine.FLYWAY);
            when(liquibaseProvider.engine()).thenReturn(MigrationEngine.LIQUIBASE);

            service = new ComparisonService(List.of(flywayProvider, liquibaseProvider), schemaInspection);
        }

        @Test
        @DisplayName("routes each engine to its own provider")
        void routesToTheRightProvider() {
            when(flywayProvider.status()).thenReturn(status(MigrationEngine.FLYWAY, 6));
            when(liquibaseProvider.status()).thenReturn(status(MigrationEngine.LIQUIBASE, 7));

            assertThat(service.status(MigrationEngine.FLYWAY).appliedCount()).isEqualTo(6);
            assertThat(service.status(MigrationEngine.LIQUIBASE).appliedCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("fails clearly when an engine has no registered provider")
        void failsWhenProviderMissing() {
            ComparisonService partial = new ComparisonService(List.of(flywayProvider), schemaInspection);

            assertThatThrownBy(() -> partial.status(MigrationEngine.LIQUIBASE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No history provider registered");
        }

        @Test
        @DisplayName("compare() assembles both statuses, both schemas and the feature matrix")
        void compareAssemblesEverything() {
            when(flywayProvider.status()).thenReturn(status(MigrationEngine.FLYWAY, 6));
            when(liquibaseProvider.status()).thenReturn(status(MigrationEngine.LIQUIBASE, 7));
            when(schemaInspection.snapshot(MigrationEngine.FLYWAY))
                    .thenReturn(snapshot(MigrationEngine.FLYWAY, List.of("PRODUCT"), List.of(), List.of()));
            when(schemaInspection.snapshot(MigrationEngine.LIQUIBASE))
                    .thenReturn(snapshot(MigrationEngine.LIQUIBASE, List.of("PRODUCT"), List.of(), List.of()));

            ComparisonReport report = service.compare();

            assertThat(report.flyway().appliedCount()).isEqualTo(6);
            assertThat(report.liquibase().appliedCount()).isEqualTo(7);
            assertThat(report.schemasEquivalent()).isTrue();
            assertThat(report.schemaDifferences()).isEmpty();
            assertThat(report.featureMatrix()).isEqualTo(FeatureMatrix.rows());
        }

        @Test
        @DisplayName("compare() flags divergence when the schemas differ")
        void compareFlagsDivergence() {
            when(flywayProvider.status()).thenReturn(status(MigrationEngine.FLYWAY, 6));
            when(liquibaseProvider.status()).thenReturn(status(MigrationEngine.LIQUIBASE, 7));
            when(schemaInspection.snapshot(MigrationEngine.FLYWAY))
                    .thenReturn(snapshot(MigrationEngine.FLYWAY, List.of("PRODUCT", "GHOST"), List.of(), List.of()));
            when(schemaInspection.snapshot(MigrationEngine.LIQUIBASE))
                    .thenReturn(snapshot(MigrationEngine.LIQUIBASE, List.of("PRODUCT"), List.of(), List.of()));

            ComparisonReport report = service.compare();

            assertThat(report.schemasEquivalent()).isFalse();
            assertThat(report.schemaDifferences()).hasSize(1);
        }

        @Test
        @DisplayName("schema() delegates to the inspection service")
        void schemaDelegates() {
            SchemaSnapshot expected = snapshot(MigrationEngine.FLYWAY, List.of("PRODUCT"), List.of(), List.of());
            when(schemaInspection.snapshot(MigrationEngine.FLYWAY)).thenReturn(expected);

            assertThat(service.schema(MigrationEngine.FLYWAY)).isSameAs(expected);
        }
    }
}
