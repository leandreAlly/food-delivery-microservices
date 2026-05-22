package com.fooddelivery.infrastructure.gateway.controller;

import com.fooddelivery.shared.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Catches all gateway routes when their Resilience4j circuit breaker opens
 * (downstream is unreachable, slow, or 5xx'ing). Returns a uniform 503
 * envelope so clients see a structured error instead of a Netty timeout.
 *
 * <p>The route filters forward here with {@code forward:/fallback/{service}}.
 * The path segment is just a label that lets us produce a more useful
 * message — no fan-out to the downstream service happens here.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Map<String, String> SERVICE_LABELS = Map.of(
            "customer-service",   "Customer Service",
            "restaurant-service", "Restaurant Service",
            "order-service",      "Order Service",
            "delivery-service",   "Delivery Service"
    );

    @RequestMapping("/{service}")
    public Mono<ResponseEntity<ErrorResponse>> serviceUnavailable(
            @PathVariable String service,
            ServerWebExchange exchange) {

        String label = SERVICE_LABELS.getOrDefault(service, "Downstream service");

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                label + " is currently unavailable. Please try again shortly.",
                exchange.getRequest().getURI().getPath());

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body));
    }
}
