package com.fooddelivery.restaurant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Restaurant aggregate root. The {@code ownerId} replaces the monolith's
 * {@code @ManyToOne Customer owner} — Restaurant Service stores only the
 * customer ID, never a JPA reference to a Customer entity.
 */
@Entity
@Table(name = "restaurants", indexes = {
        @Index(name = "idx_restaurants_city", columnList = "city"),
        @Index(name = "idx_restaurants_cuisine", columnList = "cuisineType"),
        @Index(name = "idx_restaurants_owner_id", columnList = "ownerId")
})
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

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private double rating;

    private int estimatedDeliveryMinutes;

    /** Customer Service ID — logical reference, NO foreign key. */
    @Column(nullable = false)
    private Long ownerId;

    /** Snapshot of the owner's username at creation time — used for ownership checks. */
    @Column(nullable = false)
    private String ownerUsername;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
        if (this.rating == 0) this.rating = 0.0;
    }
}
