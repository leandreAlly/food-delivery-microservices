package com.fooddelivery.shared.events;

import java.time.Instant;

/**
 * Published by Order Service when a customer cancels an order. Delivery
 * Service consumes this to mark the delivery as FAILED.
 *
 * <p>Routing key: {@code order.cancelled}
 */
public record OrderCancelledEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        String reason
) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.cancelled";
    }
}
