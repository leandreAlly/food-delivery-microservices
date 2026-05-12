package com.fooddelivery.customer.dto;

import com.fooddelivery.customer.model.Customer;

/**
 * Minimal customer view served on {@code /api/internal/customers/{id}} for
 * inter-service calls (e.g. Order Service Feign call). Excludes anything
 * sensitive — no email, no contact details beyond what an order needs.
 */
public record InternalCustomerResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String deliveryAddress,
        String city,
        String role
) {
    public static InternalCustomerResponse from(Customer c) {
        return new InternalCustomerResponse(
                c.getId(),
                c.getUsername(),
                c.getFirstName(),
                c.getLastName(),
                c.getDeliveryAddress(),
                c.getCity(),
                c.getRole().name()
        );
    }
}
