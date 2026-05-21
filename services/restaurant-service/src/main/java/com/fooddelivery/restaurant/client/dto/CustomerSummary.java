package com.fooddelivery.restaurant.client.dto;

/**
 * Local mirror of the JSON shape returned by Customer Service's
 * {@code /api/internal/customers/{username}/promote-to-owner} endpoint.
 *
 * <p>We do NOT depend on customer-service's compiled classes — that would
 * create a build-time coupling. Each service owns its own DTOs.
 */
public record CustomerSummary(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String role
) {}
