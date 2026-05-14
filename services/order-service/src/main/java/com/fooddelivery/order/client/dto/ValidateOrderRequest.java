package com.fooddelivery.order.client.dto;

import java.util.List;

/**
 * Payload sent to Restaurant Service's
 * {@code POST /api/internal/restaurants/{id}/validate-order}.
 */
public record ValidateOrderRequest(List<Item> items) {

    public record Item(Long menuItemId, int quantity) {}
}
