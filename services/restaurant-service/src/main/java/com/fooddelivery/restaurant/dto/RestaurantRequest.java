package com.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RestaurantRequest(
        @NotBlank String name,
        String description,
        @NotBlank String cuisineType,
        @NotBlank String address,
        @NotBlank String city,
        String phone,
        @Min(5) int estimatedDeliveryMinutes
) {}
