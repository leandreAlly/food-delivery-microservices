package com.fooddelivery.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads {@code X-Correlation-Id} from the incoming request (the API Gateway
 * sets it for client-facing traffic) and pushes it into SLF4J's MDC under
 * {@code correlationId} so every log line for this request is tagged.
 *
 * <p>If the header is missing, generates a new UUID — this happens for
 * service-to-service traffic that didn't go through the gateway.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationIdConstants.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
        response.setHeader(CorrelationIdConstants.HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }
    }
}
