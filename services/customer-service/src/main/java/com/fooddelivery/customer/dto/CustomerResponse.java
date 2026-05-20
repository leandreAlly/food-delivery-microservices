package com.fooddelivery.customer.dto;

import com.fooddelivery.customer.model.Customer;

import java.time.LocalDateTime;

/**
 * Public customer view. Never returns the password hash.
 */
public record CustomerResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String deliveryAddress,
        String city,
        String role,
        LocalDateTime createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getUsername(),
                c.getEmail(),
                c.getFirstName(),
                c.getLastName(),
                c.getPhone(),
                c.getDeliveryAddress(),
                c.getCity(),
                c.getRole().name(),
                c.getCreatedAt()
        );
    }
}
