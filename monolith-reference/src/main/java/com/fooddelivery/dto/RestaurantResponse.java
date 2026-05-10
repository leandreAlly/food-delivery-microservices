package com.fooddelivery.dto;

import com.fooddelivery.model.Restaurant;
import lombok.Data;
import java.util.List;

@Data
public class RestaurantResponse {
    private Long id;
    private String name;
    private String description;
    private String cuisineType;
    private String address;
    private String city;
    private String phone;
    private boolean active;
    private double rating;
    private int estimatedDeliveryMinutes;
    private int menuItemCount;

    // MONOLITH: owner info embedded via direct entity traversal
    private Long ownerId;
    private String ownerName;

    public static RestaurantResponse fromEntity(Restaurant r) {
        RestaurantResponse dto = new RestaurantResponse();
        dto.setId(r.getId());
        dto.setName(r.getName());
        dto.setDescription(r.getDescription());
        dto.setCuisineType(r.getCuisineType());
        dto.setAddress(r.getAddress());
        dto.setCity(r.getCity());
        dto.setPhone(r.getPhone());
        dto.setActive(r.isActive());
        dto.setRating(r.getRating());
        dto.setEstimatedDeliveryMinutes(r.getEstimatedDeliveryMinutes());
        dto.setMenuItemCount(r.getMenuItems() != null ? r.getMenuItems().size() : 0);
        // MONOLITH: cross-domain entity traversal
        dto.setOwnerId(r.getOwner().getId());
        dto.setOwnerName(r.getOwner().getFirstName() + " " + r.getOwner().getLastName());
        return dto;
    }
}
