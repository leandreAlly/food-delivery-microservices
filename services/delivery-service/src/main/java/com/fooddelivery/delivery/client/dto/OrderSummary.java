package com.fooddelivery.delivery.client.dto;

/**
 * Local mirror of Order Service's
 * {@code /api/internal/orders/{id}} JSON shape. Carries the snapshot data
 * Delivery Service needs to populate a delivery record.
 */
public record OrderSummary(
        Long id,
        String status,
        Long customerId,
        String customerName,
        Long restaurantId,
        String restaurantName,
        String restaurantAddress,
        String deliveryAddress
) {}
