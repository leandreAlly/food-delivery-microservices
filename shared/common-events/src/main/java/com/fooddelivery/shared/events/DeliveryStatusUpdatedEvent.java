package com.fooddelivery.shared.events;

import java.time.Instant;

/**
 * Published by Delivery Service when a driver picks up or delivers an order.
 * Order Service consumes this to advance the order's status without being
 * coupled to the Delivery domain.
 *
 * <p>Routing key: {@code delivery.status-updated}
 */
public record DeliveryStatusUpdatedEvent(
        String eventId,
        Instant occurredAt,
        Long deliveryId,
        Long orderId,
        String newStatus
) implements DomainEvent {

    @Override
    public String eventType() {
        return "delivery.status-updated";
    }
}
