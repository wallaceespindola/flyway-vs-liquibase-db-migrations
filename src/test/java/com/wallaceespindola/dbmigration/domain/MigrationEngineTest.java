package com.wallaceespindola.dbmigration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author Wallace Espindola
 */
class MigrationEngineTest {

    @ParameterizedTest
    @CsvSource({
        "flyway,FLYWAY",
        "FLYWAY,FLYWAY",
        "FlyWay,FLYWAY",
        "liquibase,LIQUIBASE",
        "LIQUIBASE,LIQUIBASE",
        "LiquiBase,LIQUIBASE"
    })
    @DisplayName("resolves engine names case-insensitively")
    void resolvesCaseInsensitively(String input, MigrationEngine expected) {
        assertThat(MigrationEngine.from(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mongodb", "", "  ", "fly way", "liquibase2"})
    @DisplayName("rejects unknown engine names with a helpful message")
    void rejectsUnknownNames(String input) {
        assertThatThrownBy(() -> MigrationEngine.from(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown migration engine")
                .hasMessageContaining("flyway, liquibase");
    }

    @Test
    @DisplayName("rejects a null engine name")
    void rejectsNull() {
        assertThatThrownBy(() -> MigrationEngine.from(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(MigrationEngine.class)
    @DisplayName("every engine carries display metadata")
    void everyEngineHasMetadata(MigrationEngine engine) {
        assertThat(engine.displayName()).isNotBlank();
        assertThat(engine.historyTable()).isNotBlank();
        assertThat(engine.model()).isNotBlank();
    }

    @Test
    @DisplayName("each engine names its own bookkeeping table")
    void bookkeepingTablesAreDistinct() {
        assertThat(MigrationEngine.FLYWAY.historyTable()).isEqualTo("flyway_schema_history");
        assertThat(MigrationEngine.LIQUIBASE.historyTable()).isEqualTo("DATABASECHANGELOG");
    }
}
