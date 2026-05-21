package com.fooddelivery.restaurant.dto;

import com.fooddelivery.restaurant.model.MenuItem;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean available,
        String imageUrl,
        Long restaurantId
) {
    public static MenuItemResponse from(MenuItem m) {
        return new MenuItemResponse(
                m.getId(), m.getName(), m.getDescription(),
                m.getPrice(), m.getCategory(), m.isAvailable(),
                m.getImageUrl(), m.getRestaurant().getId());
    }
}
