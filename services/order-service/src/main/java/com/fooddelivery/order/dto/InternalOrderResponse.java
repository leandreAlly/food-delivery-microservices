package com.fooddelivery.order.dto;

import com.fooddelivery.order.model.Order;

/**
 * Minimal order view returned on {@code /api/internal/orders/{id}} for
 * Delivery Service. Carries the snapshot data Delivery needs to create
 * a delivery record without further fan-out.
 */
public record InternalOrderResponse(
        Long id,
        String status,
        Long customerId,
        String customerName,
        Long restaurantId,
        String restaurantName,
        String restaurantAddress,
        String deliveryAddress
) {
    public static InternalOrderResponse from(Order o) {
        return new InternalOrderResponse(
                o.getId(), o.getStatus().name(),
                o.getCustomerId(), o.getCustomerName(),
                o.getRestaurantId(), o.getRestaurantName(), o.getRestaurantAddress(),
                o.getDeliveryAddress());
    }
}
