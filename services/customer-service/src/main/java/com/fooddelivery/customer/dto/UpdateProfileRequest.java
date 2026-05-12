package com.fooddelivery.customer.dto;

/**
 * Partial update — any non-null field is applied. Username, email,
 * password, and role are intentionally NOT updatable here.
 */
public record UpdateProfileRequest(
        String firstName,
        String lastName,
        String phone,
        String deliveryAddress,
        String city
) {}
