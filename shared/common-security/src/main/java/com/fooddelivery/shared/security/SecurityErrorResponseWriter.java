package com.fooddelivery.shared.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes the shared {@code ErrorResponse} JSON shape directly to a servlet
 * response. Used by {@link JsonAuthenticationEntryPoint} and
 * {@link JsonAccessDeniedHandler} so unauthenticated / forbidden requests get
 * the same envelope as every other API error.
 *
 * <p>Built without Jackson — JSON is hand-assembled with escaping — so this
 * class adds no new transitive dependencies to common-security.
 */
final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {}

    static void write(HttpServletResponse response,
                      int status,
                      String error,
                      String message,
                      String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String body = "{"
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":"      + status + ","
                + "\"error\":\""     + escape(error)   + "\","
                + "\"message\":\""   + escape(message) + "\","
                + "\"path\":\""      + escape(path)    + "\","
                + "\"fieldErrors\":null"
                + "}";

        response.getWriter().write(body);
        response.getWriter().flush();
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
