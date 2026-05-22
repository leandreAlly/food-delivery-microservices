package com.fooddelivery.infrastructure.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.shared.security.JwtVerifier;
import com.fooddelivery.shared.security.SecurityHeaders;
import com.fooddelivery.shared.web.ErrorResponse;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Validates {@code Authorization: Bearer <token>} on every incoming request,
 * then injects {@code X-User-Id} / {@code X-User-Username} / {@code X-User-Role}
 * headers on the outbound request before routing it to the downstream service.
 *
 * <p>Public paths bypass the filter:
 * <ul>
 *   <li>{@code /api/auth/**} — registration and login</li>
 *   <li>{@code GET /api/restaurants/search/**} — public catalog browse</li>
 *   <li>{@code GET /api/restaurants/&#42;/menu} — public menu read</li>
 *   <li>{@code /actuator/**} — health/info</li>
 * </ul>
 *
 * <p>Downstream services still validate the JWT locally as defense in depth
 * (Phase 6/7 plumbing) — that's fine, the gateway is the first line.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> ALWAYS_PUBLIC_PREFIXES = List.of(
            "/api/auth/",
            "/actuator/"
    );

    private static final List<String> GET_ONLY_PUBLIC_PATTERNS = List.of(
            "/api/restaurants/search/**",
            "/api/restaurants/*/menu"
    );

    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;

    public JwtAuthGlobalFilter(JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublic(path, request.getMethod())) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring("Bearer ".length());
        Optional<Claims> maybeClaims = jwtVerifier.tryVerify(token);
        if (maybeClaims.isEmpty()) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        Claims claims = maybeClaims.get();
        Long userId    = claims.get("uid", Long.class);
        String username = claims.getSubject();
        String role     = claims.get("role", String.class);

        if (userId == null || username == null || role == null) {
            return unauthorized(exchange, "Token is missing required claims");
        }

        // Mutate the request so the downstream service sees the trusted
        // X-User-* headers we just derived from the token.
        ServerHttpRequest mutated = request.mutate()
                .header(SecurityHeaders.X_USER_ID,       String.valueOf(userId))
                .header(SecurityHeaders.X_USER_USERNAME, username)
                .header(SecurityHeaders.X_USER_ROLE,     role)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // Run early so headers are injected before the routing / load-balancer
        // filters (those sit at order 10000+). Negative = high precedence.
        return -100;
    }

    // ---- helpers ----

    private boolean isPublic(String path, HttpMethod method) {
        for (String prefix : ALWAYS_PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        if (HttpMethod.GET.equals(method)) {
            for (String pattern : GET_ONLY_PUBLIC_PATTERNS) {
                if (PATH_MATCHER.match(pattern, path)) return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        log.debug("Rejecting {} {}: {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(),
                message);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                message,
                exchange.getRequest().getURI().getPath());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            // Should never happen for a tiny record, but fail safe.
            return response.setComplete();
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
