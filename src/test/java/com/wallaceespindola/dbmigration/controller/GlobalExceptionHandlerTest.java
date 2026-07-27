package com.wallaceespindola.dbmigration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallaceespindola.dbmigration.dto.ApiResponse;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The bad-request path is also exercised end to end in {@code ApplicationIntegrationTest}; these
 * tests cover the branches that are hard to trigger through a live request.
 *
 * @author Wallace Espindola
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("an illegal argument becomes a 400 carrying the original message")
    void illegalArgumentBecomesBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequest(new IllegalArgumentException("Unknown migration engine 'mongodb'"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Unknown migration engine");
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("a database failure becomes a 503 naming the most specific cause")
    void dataAccessBecomesServiceUnavailable() {
        var exception = new DataAccessResourceFailureException("pool exhausted", new SQLException("no connection"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataAccess(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Database unavailable").contains("no connection");
    }

    @Test
    @DisplayName("anything else becomes a 500 without leaking a stack trace")
    void unexpectedBecomesInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new IllegalStateException("bean not ready"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Unexpected error: bean not ready");
        assertThat(response.getBody().data()).isNull();
    }
}
