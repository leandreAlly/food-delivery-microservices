package com.fooddelivery.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.jwt.*} configuration. Each service that issues or verifies
 * JWTs must enable this via {@code @EnableConfigurationProperties(JwtProperties.class)}.
 *
 * <p>The {@code secret} should always come from an environment variable in
 * Docker / production, never be committed in application.yml.
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs = 86_400_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
