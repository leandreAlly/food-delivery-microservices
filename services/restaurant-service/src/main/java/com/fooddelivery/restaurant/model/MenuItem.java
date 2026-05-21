package com.fooddelivery.restaurant.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Menu item. Child of Restaurant — same aggregate, so the FK back to
 * restaurants is real and enforced inside restaurant_db.
 */
@Entity
@Table(name = "menu_items", indexes = {
        @Index(name = "idx_menu_items_restaurant_id", columnList = "restaurant_id"),
        @Index(name = "idx_menu_items_restaurant_available",
               columnList = "restaurant_id, available")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    private String category;

    @Column(nullable = false)
    private boolean available;

    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @PrePersist
    void onCreate() {
        this.available = true;
    }
}
