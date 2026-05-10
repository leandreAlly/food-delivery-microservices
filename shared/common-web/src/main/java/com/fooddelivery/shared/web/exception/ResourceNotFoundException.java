package com.fooddelivery.shared.web.exception;

/**
 * Thrown when a requested resource doesn't exist. Maps to HTTP 404 in
 * {@link AbstractGlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super("%s not found with %s = %s".formatted(resource, field, value));
    }
}
