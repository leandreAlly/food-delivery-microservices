package com.fooddelivery.delivery.exception;

/**
 * Thrown when Order Service is unreachable (timeout, circuit open).
 * Maps to HTTP 503.
 */
public class OrderServiceUnavailableException extends RuntimeException {

    public OrderServiceUnavailableException(String message) {
        super(message);
    }
}
