package com.fooddelivery.order.client.dto;

/**
 * Local mirror of Customer Service's
 * {@code /api/internal/customers/{id}} JSON shape. Each service owns its
 * own DTO — Order Service never depends on customer-service compiled classes.
 */
public record CustomerSummary(
        Long id,
        String username,
        String firstName,
        String lastName,
        String deliveryAddress,
        String city,
        String role
) {
    public String fullName() {
        return ((firstName == null ? "" : firstName) + " "
              + (lastName  == null ? "" : lastName )).trim();
    }
}
