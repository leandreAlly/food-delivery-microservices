package com.fooddelivery.shared.security;

/**
 * Custom HTTP headers the API Gateway injects after validating a JWT.
 * Downstream services trust these headers and never re-verify the token.
 */
public final class SecurityHeaders {

    public static final String X_USER_ID       = "X-User-Id";
    public static final String X_USER_USERNAME = "X-User-Username";
    public static final String X_USER_ROLE     = "X-User-Role";

    private SecurityHeaders() {}
}
