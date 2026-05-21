package com.fooddelivery.order.client;

import com.fooddelivery.order.client.dto.CustomerSummary;
import com.fooddelivery.order.exception.CustomerServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomerServiceClientFallback implements CustomerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceClientFallback.class);

    @Override
    public CustomerSummary getById(Long id) {
        log.warn("Customer Service unavailable — getById fallback triggered (id={})", id);
        throw new CustomerServiceUnavailableException(
                "Cannot place order right now — Customer Service is unavailable.");
    }
}
