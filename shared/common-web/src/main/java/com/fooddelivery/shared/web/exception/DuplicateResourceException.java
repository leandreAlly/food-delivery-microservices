package com.fooddelivery.shared.web.exception;

/**
 * Thrown when a uniqueness constraint would be violated (e.g. existing
 * username at register). Maps to HTTP 409 in {@link AbstractGlobalExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
