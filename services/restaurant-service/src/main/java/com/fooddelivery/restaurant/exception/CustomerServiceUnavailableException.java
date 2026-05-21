package com.fooddelivery.restaurant.exception;

/**
 * Thrown when a downstream call to Customer Service fails (timeout, circuit
 * breaker open, network error). Maps to HTTP 503 in the exception handler.
 */
public class CustomerServiceUnavailableException extends RuntimeException {

    public CustomerServiceUnavailableException(String message) {
        super(message);
    }
}
