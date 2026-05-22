package com.fooddelivery.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Triggered by Spring Security when an {@link AuthenticationException} reaches
 * the {@code ExceptionTranslationFilter} — typically because no credentials
 * were supplied at all. Writes a JSON {@code 401 Unauthorized} matching the
 * shared {@code ErrorResponse} envelope.
 *
 * <p>Pair this with {@link JsonAccessDeniedHandler} in each service's
 * security config so anonymous users also get 401 (instead of Spring's
 * default 403).
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception)
            throws IOException, ServletException {
        SecurityErrorResponseWriter.write(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                exception.getMessage() != null
                        ? exception.getMessage()
                        : "Authentication required",
                request.getRequestURI());
    }
}
