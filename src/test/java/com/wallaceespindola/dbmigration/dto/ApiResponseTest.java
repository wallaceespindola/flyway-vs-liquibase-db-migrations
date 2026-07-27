package com.wallaceespindola.dbmigration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author Wallace Espindola
 */
class ApiResponseTest {

    @Test
    @DisplayName("ok() carries the payload, a message and a timestamp")
    void okCarriesPayload() {
        Instant before = Instant.now();

        ApiResponse<List<String>> response = ApiResponse.ok(List.of("a", "b"), "two items");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly("a", "b");
        assertThat(response.message()).isEqualTo("two items");
        assertThat(response.timestamp()).isBetween(before, Instant.now());
    }

    @Test
    @DisplayName("error() has no payload but still carries a timestamp")
    void errorHasNoPayload() {
        ApiResponse<String> response = ApiResponse.error("something broke");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo("something broke");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("ok() accepts a null payload without failing")
    void okAcceptsNullPayload() {
        assertThat(ApiResponse.ok(null, "nothing").data()).isNull();
    }
}
