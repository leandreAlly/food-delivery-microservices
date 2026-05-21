package com.fooddelivery.restaurant.dto;

import java.util.List;

/**
 * Response to {@link ValidateOrderRequest}. Carries everything Order Service
 * needs to snapshot the order without further fan-out calls.
 */
public record ValidatedOrderResponse(
        Long restaurantId,
        String restaurantName,
        String restaurantAddress,
        boolean restaurantActive,
        int estimatedDeliveryMinutes,
        List<ValidatedOrderItem> items
) {}
