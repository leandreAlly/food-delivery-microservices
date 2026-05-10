package com.fooddelivery.shared.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by Order Service after an order is committed. Carries enough
 * data for Delivery Service to create a delivery assignment without
 * calling any other service.
 *
 * <p>Routing key: {@code order.placed}
 */
public record OrderPlacedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        Long customerId,
        String customerName,
        Long restaurantId,
        String restaurantName,
        String restaurantAddress,
        String deliveryAddress,
        BigDecimal totalAmount
) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.placed";
    }
}
