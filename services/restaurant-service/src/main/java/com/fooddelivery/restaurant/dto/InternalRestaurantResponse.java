package com.fooddelivery.restaurant.dto;

import com.fooddelivery.restaurant.model.Restaurant;

/**
 * Minimal restaurant view served on {@code /api/internal/restaurants/{id}}
 * for inter-service calls.
 */
public record InternalRestaurantResponse(
        Long id,
        String name,
        String address,
        String city,
        boolean active,
        int estimatedDeliveryMinutes
) {
    public static InternalRestaurantResponse from(Restaurant r) {
        return new InternalRestaurantResponse(
                r.getId(), r.getName(), r.getAddress(), r.getCity(),
                r.isActive(), r.getEstimatedDeliveryMinutes());
    }
}
