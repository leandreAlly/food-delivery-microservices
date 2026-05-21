package com.fooddelivery.order.exception;

/**
 * Thrown when Restaurant Service is unreachable (timeout, circuit open).
 * Maps to HTTP 503.
 */
public class RestaurantServiceUnavailableException extends RuntimeException {

    public RestaurantServiceUnavailableException(String message) {
        super(message);
    }
}
