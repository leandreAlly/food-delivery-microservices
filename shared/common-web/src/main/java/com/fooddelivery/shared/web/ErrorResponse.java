package com.fooddelivery.shared.web;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error envelope returned by every service. Keeping the shape
 * identical across services means clients (and the gateway) can handle
 * errors generically.
 *
 * @param fieldErrors  null unless this is a validation failure
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse validation(String message, String path,
                                           Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), 400, "Bad Request",
                message, path, fieldErrors);
    }
}
