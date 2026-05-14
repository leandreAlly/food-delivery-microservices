package com.fooddelivery.order.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order aggregate root. All cross-domain entity references from the monolith
 * are now {@code Long} IDs plus snapshot strings. Customer name, restaurant
 * name, and restaurant address are denormalized at write time so reads
 * never fan out to other services.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_customer_id",   columnList = "customerId"),
        @Index(name = "idx_orders_restaurant_id", columnList = "restaurantId"),
        @Index(name = "idx_orders_status",        columnList = "status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private BigDecimal deliveryFee;

    @Column(nullable = false)
    private String deliveryAddress;

    private String specialInstructions;

    // ---- Logical references + snapshot fields ----

    /** Customer Service ID — no FK, no JPA reference. */
    @Column(nullable = false)
    private Long customerId;

    /** Snapshot of "FirstName LastName" at order time. */
    @Column(nullable = false)
    private String customerName;

    /** Snapshot of the customer's username — used for ownership checks on cancel. */
    @Column(nullable = false)
    private String customerUsername;

    /** Restaurant Service ID — no FK. */
    @Column(nullable = false)
    private Long restaurantId;

    /** Snapshot — protects historical orders from later renames. */
    @Column(nullable = false)
    private String restaurantName;

    @Column(nullable = false)
    private String restaurantAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    private LocalDateTime estimatedDeliveryTime;

    // ---- Same-aggregate children (real FK inside order_db) ----

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = OrderStatus.PLACED;
        if (this.deliveryFee == null) this.deliveryFee = new BigDecimal("2.99");
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum OrderStatus {
        PLACED,
        CONFIRMED,
        PREPARING,
        READY_FOR_PICKUP,
        OUT_FOR_DELIVERY,
        DELIVERED,
        CANCELLED
    }
}
