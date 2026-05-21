package com.fooddelivery.order.client.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Local mirror of Restaurant Service's validate-order response. Contains
 * everything Order Service needs to snapshot the order without further calls.
 */
public record ValidatedOrderResponse(
        Long restaurantId,
        String restaurantName,
        String restaurantAddress,
        boolean restaurantActive,
        int estimatedDeliveryMinutes,
        List<ValidatedItem> items
) {
    public record ValidatedItem(
            Long menuItemId,
            String menuItemName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal,
            boolean available
    ) {}
}
