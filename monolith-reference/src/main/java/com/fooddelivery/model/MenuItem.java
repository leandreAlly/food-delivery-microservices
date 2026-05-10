package com.fooddelivery.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * MenuItem entity — part of the Restaurant domain.
 *
 * This entity stays with Restaurant in the microservices split.
 * The coupling problem here is that OrderItem directly references
 * MenuItem via JPA, which must become an ID reference + REST call.
 */
@Entity
@Table(name = "menu_items")
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

    private boolean available;

    private String imageUrl;

    // ---- SAME-DOMAIN RELATIONSHIP (this is fine) ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @PrePersist
    protected void onCreate() {
        available = true;
    }
}
