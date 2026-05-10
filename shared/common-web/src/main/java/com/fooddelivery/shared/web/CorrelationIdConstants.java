package com.fooddelivery.shared.web;

/**
 * Constants used by {@link CorrelationIdFilter} and the Feign request
 * interceptor that propagates the ID across service calls.
 */
public final class CorrelationIdConstants {

    public static final String HEADER  = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdConstants() {}
}
