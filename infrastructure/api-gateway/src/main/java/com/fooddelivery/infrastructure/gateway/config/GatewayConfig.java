package com.fooddelivery.infrastructure.gateway.config;

import com.fooddelivery.shared.security.JwtProperties;
import com.fooddelivery.shared.security.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway-wide beans. The {@link JwtVerifier} validates incoming Bearer
 * tokens; the {@code JwtAuthGlobalFilter} consumes it.
 *
 * <p>No password encoder / no JwtIssuer — the gateway only verifies tokens,
 * never creates them.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties properties) {
        return new JwtVerifier(properties);
    }
}
