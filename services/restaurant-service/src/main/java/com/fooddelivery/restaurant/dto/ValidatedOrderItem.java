package com.fooddelivery.restaurant.dto;

import java.math.BigDecimal;

public record ValidatedOrderItem(
        Long menuItemId,
        String menuItemName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        boolean available
) {}
