package com.fooddelivery.order.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Idempotency ledger for inbound RabbitMQ events. Inserting an
 * {@code eventId} that already exists fails the PK constraint —
 * the consumer treats that as "already handled, no-op".
 */
@Entity
@Table(name = "processed_events_order")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(length = 64)
    private String eventId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;
}
