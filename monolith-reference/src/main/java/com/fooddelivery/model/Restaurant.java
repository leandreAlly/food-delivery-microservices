package com.fooddelivery.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Restaurant entity — part of the Restaurant domain.
 *
 * MONOLITH PROBLEM: Direct @ManyToOne to Customer (as owner)
 * and @OneToMany to MenuItem and Order. In microservices,
 * the Restaurant Service should only store ownerId (Long)
 * and validate via REST call to Customer Service.
 */
@Entity
@Table(name = "restaurants")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String cuisineType;
    private String address;
    private String city;
    private String phone;

    private boolean active;

    @Column(nullable = false)
    private double rating;

    private int estimatedDeliveryMinutes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ---- CROSS-DOMAIN RELATIONSHIPS (monolith anti-pattern) ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Customer owner;

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MenuItem> menuItems = new ArrayList<>();

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL)
    private List<Order> orders = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (rating == 0) rating = 0.0;
        active = true;
    }
}
