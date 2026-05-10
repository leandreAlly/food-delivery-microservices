package com.fooddelivery.shared.security;

/**
 * Thrown when a request is missing or has invalid authentication.
 * Maps to HTTP 401 via the service's global exception handler.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
