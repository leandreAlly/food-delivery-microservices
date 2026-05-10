package com.fooddelivery.shared.security;

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

/**
 * Servlet filter run by every downstream microservice. It does NOT validate
 * JWTs — that already happened at the API Gateway. It just reads the
 * X-User-* headers the gateway forwards and lifts them into Spring's
 * {@code SecurityContextHolder} as an {@link AuthenticatedUser}.
 *
 * <p>If the headers are missing the request stays unauthenticated; the
 * service's own {@code SecurityFilterChain} decides whether that's a 401.
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String idHeader = request.getHeader(SecurityHeaders.X_USER_ID);
        String username = request.getHeader(SecurityHeaders.X_USER_USERNAME);
        String role     = request.getHeader(SecurityHeaders.X_USER_ROLE);

        if (idHeader != null && username != null && role != null) {
            AuthenticatedUser principal = new AuthenticatedUser(
                    Long.parseLong(idHeader), username, role);

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }
}
