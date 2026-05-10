package com.fooddelivery.shared.events;

import java.time.Instant;

/**
 * Every event flowing through RabbitMQ implements this. The {@code eventId}
 * is a UUID used by consumers for idempotency — see the {@code processed_events_*}
 * tables in {@code order_db} and {@code delivery_db}.
 */
public interface DomainEvent {

    String eventId();

    String eventType();

    Instant occurredAt();
}
