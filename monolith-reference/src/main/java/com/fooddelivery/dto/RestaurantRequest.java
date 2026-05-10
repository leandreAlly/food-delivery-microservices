package com.fooddelivery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RestaurantRequest {
    @NotBlank private String name;
    private String description;
    @NotBlank private String cuisineType;
    @NotBlank private String address;
    @NotBlank private String city;
    private String phone;
    private int estimatedDeliveryMinutes;
}
