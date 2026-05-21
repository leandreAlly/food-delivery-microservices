package com.fooddelivery.shared.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Validates {@code Authorization: Bearer <token>} and places an
 * {@link AuthenticatedUser} principal in the SecurityContext. Used by every
 * downstream service that wants to validate JWTs directly (defense in depth)
 * in addition to gateway validation.
 *
 * <p>Promoted from customer-service in Phase 5 once restaurant-service needed
 * the same logic — extract shared code after two callers exist, not before.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Optional<Claims> claims = jwtVerifier.tryVerify(token);
            claims.ifPresent(this::authenticate);
        }

        chain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        Long id     = claims.get("uid", Long.class);
        String name = claims.getSubject();
        String role = claims.get("role", String.class);

        AuthenticatedUser principal = new AuthenticatedUser(id, name, role);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
