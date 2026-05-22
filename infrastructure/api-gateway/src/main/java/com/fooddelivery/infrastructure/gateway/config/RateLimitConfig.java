package com.fooddelivery.infrastructure.gateway.config;

import com.fooddelivery.shared.security.SecurityHeaders;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate-limit key strategy: bucket per authenticated user.
 *
 * <p>The {@code X-User-Id} header is set by {@code JwtAuthGlobalFilter} after
 * a successful token validation, so by the time the {@code RequestRateLimiter}
 * filter runs it's always present on the protected route this is applied to
 * (POST /api/orders). The unauthenticated fallback never fires in practice,
 * but having it makes the bean safe to reuse on a public route later.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Bean name {@code userKeyResolver} is referenced from application.yml
     * as {@code #{@userKeyResolver}} in the route filter args.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders()
                    .getFirst(SecurityHeaders.X_USER_ID);
            return Mono.justOrEmpty(userId).switchIfEmpty(Mono.just("anonymous"));
        };
    }
}
