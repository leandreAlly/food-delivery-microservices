package com.fooddelivery.order.exception;

/**
 * Thrown when Customer Service is unreachable (timeout, circuit open).
 * Maps to HTTP 503.
 */
public class CustomerServiceUnavailableException extends RuntimeException {

    public CustomerServiceUnavailableException(String message) {
        super(message);
    }
}
