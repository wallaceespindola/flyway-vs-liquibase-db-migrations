package com.wallaceespindola.dbmigration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wallaceespindola.dbmigration.dto.FeatureComparison;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author Wallace Espindola
 */
class FeatureMatrixTest {

    @Test
    @DisplayName("the matrix is populated")
    void matrixIsPopulated() {
        assertThat(FeatureMatrix.rows()).hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("every row is fully filled in")
    void everyRowIsComplete() {
        assertThat(FeatureMatrix.rows()).allSatisfy(row -> {
            assertThat(row.feature()).isNotBlank();
            assertThat(row.flyway()).isNotBlank();
            assertThat(row.liquibase()).isNotBlank();
            assertThat(row.edge()).isNotBlank();
        });
    }

    @Test
    @DisplayName("edge is always one of the three known verdicts")
    void edgeIsAlwaysKnown() {
        assertThat(FeatureMatrix.rows())
                .extracting(FeatureComparison::edge)
                .allMatch(edge -> List.of("FLYWAY", "LIQUIBASE", "TIE").contains(edge));
    }

    @Test
    @DisplayName("no feature is listed twice")
    void featuresAreUnique() {
        List<String> features = FeatureMatrix.rows().stream().map(FeatureComparison::feature).toList();
        assertThat(features).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("the comparison is balanced — neither engine wins every row")
    void comparisonIsBalanced() {
        List<FeatureComparison> rows = FeatureMatrix.rows();
        assertThat(rows).anyMatch(row -> "FLYWAY".equals(row.edge()));
        assertThat(rows).anyMatch(row -> "LIQUIBASE".equals(row.edge()));
        assertThat(rows).anyMatch(row -> "TIE".equals(row.edge()));
    }

    @Test
    @DisplayName("the returned list is immutable")
    void listIsImmutable() {
        List<FeatureComparison> rows = FeatureMatrix.rows();
        assertThatThrownBy(() -> rows.add(new FeatureComparison("x", "y", "z", "TIE")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("the utility class cannot be instantiated")
    void cannotBeInstantiated() throws Exception {
        Constructor<FeatureMatrix> constructor = FeatureMatrix.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance).hasRootCauseInstanceOf(AssertionError.class);
    }
}
