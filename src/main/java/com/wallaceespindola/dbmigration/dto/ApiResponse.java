package com.wallaceespindola.dbmigration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Envelope returned by every REST endpoint, carrying a server timestamp alongside the payload.
 *
 * @param success whether the call succeeded
 * @param message human-readable summary
 * @param data the payload, {@code null} on failure
 * @param timestamp server-side instant the response was produced
 * @param <T> payload type
 * @author Wallace Espindola
 */
@Schema(description = "Standard response envelope with a server timestamp")
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
