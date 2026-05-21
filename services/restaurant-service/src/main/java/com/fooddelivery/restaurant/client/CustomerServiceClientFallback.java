package com.fooddelivery.restaurant.client;

import com.fooddelivery.restaurant.client.dto.CustomerSummary;
import com.fooddelivery.restaurant.exception.CustomerServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resilience4j fallback when Customer Service is down or the call times out.
 * Throws a clear exception that maps to HTTP 503 — better than letting Feign
 * surface a generic timeout to the API caller.
 */
@Component
public class CustomerServiceClientFallback implements CustomerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceClientFallback.class);

    @Override
    public CustomerSummary promoteToOwner(String username) {
        log.warn("Customer Service unavailable — promote-to-owner fallback triggered for username={}",
                username);
        throw new CustomerServiceUnavailableException(
                "Cannot promote user to RESTAURANT_OWNER right now — Customer Service is unavailable. "
                + "Please retry shortly.");
    }
}
