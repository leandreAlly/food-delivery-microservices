package com.fooddelivery.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Verifies HS256 JWTs and extracts claims. Used primarily by the API Gateway;
 * downstream services normally trust the gateway-forwarded X-User-* headers
 * instead of re-verifying.
 */
public class JwtVerifier {

    private final JwtProperties properties;

    public JwtVerifier(JwtProperties properties) {
        this.properties = properties;
    }

    public Optional<Claims> tryVerify(String token) {
        try {
            return Optional.of(verify(token));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
