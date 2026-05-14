package com.fooddelivery.order.client;

import com.fooddelivery.order.client.dto.CustomerSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for Customer Service. Resolved via Eureka; load balanced;
 * wrapped in a Resilience4j circuit breaker via
 * {@code spring.cloud.openfeign.circuitbreaker.enabled=true}.
 */
@FeignClient(
        name = "customer-service",
        fallback = CustomerServiceClientFallback.class
)
public interface CustomerServiceClient {

    @GetMapping("/api/internal/customers/{id}")
    CustomerSummary getById(@PathVariable Long id);
}
