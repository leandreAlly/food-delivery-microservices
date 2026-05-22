package com.fooddelivery.delivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Idempotency ledger for inbound RabbitMQ events (order.placed,
 * order.cancelled). Each event id is recorded inside the same DB
 * transaction as the delivery write; duplicate redeliveries are detected
 * by the PK violation and treated as no-ops.
 */
@Entity
@Table(name = "processed_events_delivery")
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
