package com.fooddelivery.order.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Order line item. Child of {@link Order} — same aggregate, real FK inside
 * {@code order_db}. The cross-domain {@code @ManyToOne MenuItem} from the
 * monolith becomes {@code menuItemId} plus snapshot fields ({@code menuItemName},
 * {@code unitPrice}). Renaming a dish on Restaurant Service does not retroactively
 * change a six-month-old order — by design.
 */
@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "order_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Restaurant Service menu item ID — no FK. */
    @Column(nullable = false)
    private Long menuItemId;

    /** Snapshot of the item name at order time. */
    @Column(nullable = false)
    private String menuItemName;

    @Column(nullable = false)
    private int quantity;

    /** Snapshot of the price at order time. */
    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal subtotal;

    private String specialInstructions;
}
