package com.fooddelivery.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Signs HS256 JWTs. Only the Customer Service issues tokens
 * (at register / login); every other service only verifies.
 */
public class JwtIssuer {

    private final JwtProperties properties;

    public JwtIssuer(JwtProperties properties) {
        this.properties = properties;
    }

    public String issue(Long userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpirationMs());

        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
