package com.fooddelivery.shared.security;

/**
 * Lightweight principal placed in the SecurityContext by
 * {@link HeaderAuthenticationFilter}. Carries just enough to authorize
 * the current request without going back to Customer Service.
 */
public record AuthenticatedUser(Long id, String username, String role) {
}
