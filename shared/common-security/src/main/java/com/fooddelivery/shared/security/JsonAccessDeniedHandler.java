package com.fooddelivery.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Handles {@link AccessDeniedException} the way HTTP semantics expect:
 *
 * <ul>
 *   <li>Anonymous principals → <strong>401 Unauthorized</strong>
 *       (Spring's default is 403 here, which is misleading because the
 *       caller has no credentials to begin with).</li>
 *   <li>Authenticated principals missing the required authority → 403 Forbidden.</li>
 * </ul>
 *
 * <p>Each service wires this into its {@code SecurityConfig} alongside
 * {@link JsonAuthenticationEntryPoint}.
 */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException exception)
            throws IOException, ServletException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken;

        if (anonymous) {
            SecurityErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized",
                    "Authentication required",
                    request.getRequestURI());
        } else {
            SecurityErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden",
                    exception.getMessage() != null
                            ? exception.getMessage()
                            : "Access denied",
                    request.getRequestURI());
        }
    }
}
