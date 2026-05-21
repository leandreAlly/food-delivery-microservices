package com.fooddelivery.order.client;

import com.fooddelivery.order.client.dto.ValidateOrderRequest;
import com.fooddelivery.order.client.dto.ValidatedOrderResponse;
import com.fooddelivery.order.exception.RestaurantServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RestaurantServiceClientFallback implements RestaurantServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RestaurantServiceClientFallback.class);

    @Override
    public ValidatedOrderResponse validateOrder(Long id, ValidateOrderRequest request) {
        log.warn("Restaurant Service unavailable — validateOrder fallback triggered (restaurantId={})", id);
        throw new RestaurantServiceUnavailableException(
                "Cannot place order right now — Restaurant Service is unavailable.");
    }
}
