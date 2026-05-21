package com.fooddelivery.restaurant.dto;

import com.fooddelivery.restaurant.model.Restaurant;

import java.time.LocalDateTime;

public record RestaurantResponse(
        Long id,
        String name,
        String description,
        String cuisineType,
        String address,
        String city,
        String phone,
        boolean active,
        double rating,
        int estimatedDeliveryMinutes,
        Long ownerId,
        LocalDateTime createdAt
) {
    public static RestaurantResponse from(Restaurant r) {
        return new RestaurantResponse(
                r.getId(), r.getName(), r.getDescription(), r.getCuisineType(),
                r.getAddress(), r.getCity(), r.getPhone(), r.isActive(),
                r.getRating(), r.getEstimatedDeliveryMinutes(),
                r.getOwnerId(), r.getCreatedAt());
    }
}
