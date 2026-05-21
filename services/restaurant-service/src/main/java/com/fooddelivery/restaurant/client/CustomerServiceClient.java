package com.fooddelivery.restaurant.client;

import com.fooddelivery.restaurant.client.dto.CustomerSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Feign client for Customer Service. Resolved through Eureka by logical
 * name {@code customer-service}; load balanced automatically. Wrapped in
 * a Resilience4j circuit breaker via
 * {@code spring.cloud.openfeign.circuitbreaker.enabled=true}.
 */
@FeignClient(
        name = "customer-service",
        fallback = CustomerServiceClientFallback.class
)
public interface CustomerServiceClient {

    @PostMapping("/api/internal/customers/{username}/promote-to-owner")
    CustomerSummary promoteToOwner(@PathVariable String username);
}
