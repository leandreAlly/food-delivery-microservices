package com.fooddelivery.delivery.client;

import com.fooddelivery.delivery.client.dto.OrderSummary;
import com.fooddelivery.delivery.exception.OrderServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderServiceClientFallback implements OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClientFallback.class);

    @Override
    public OrderSummary getById(Long id) {
        log.warn("Order Service unavailable — getById fallback triggered (orderId={})", id);
        throw new OrderServiceUnavailableException(
                "Cannot create delivery right now — Order Service is unavailable.");
    }
}
