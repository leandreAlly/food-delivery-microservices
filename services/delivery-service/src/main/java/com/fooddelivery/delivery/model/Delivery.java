package com.fooddelivery.delivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Delivery aggregate. The monolith's {@code @OneToOne Order} reference is
 * replaced with {@code orderId: Long} plus snapshot fields for everything
 * the driver needs (customer name, pickup + delivery addresses). After
 * creation, Delivery Service never calls Order Service again.
 */
@Entity
@Table(name = "deliveries", indexes = {
        @Index(name = "idx_deliveries_order_id", columnList = "orderId", unique = true),
        @Index(name = "idx_deliveries_status",   columnList = "status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Order Service ID — logical reference, no FK, unique (one delivery per order). */
    @Column(nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    private String driverName;
    private String driverPhone;

    /** Snapshot — restaurant pickup address at assignment time. */
    private String pickupAddress;

    /** Snapshot — customer delivery address at assignment time. */
    private String deliveryAddress;

    /** Snapshot — customer name for driver-facing UI / notifications. */
    private String customerName;

    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = DeliveryStatus.PENDING;
    }

    public enum DeliveryStatus {
        PENDING,
        ASSIGNED,
        PICKED_UP,
        IN_TRANSIT,
        DELIVERED,
        FAILED
    }
}
