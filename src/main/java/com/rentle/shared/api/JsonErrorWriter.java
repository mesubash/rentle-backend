package com.rentle.shared.api;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes the {@code {data,error,timestamp}} envelope for error responses raised in
 * the filter/interceptor layer (rate limiting, authentication, access-denied),
 * where the normal @RestControllerAdvice does not run. Hand-rolled to avoid a
 * bean-ordering dependency on the Jackson ObjectMapper during MVC setup.
 */
public final class JsonErrorWriter {

    private JsonErrorWriter() {}

    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"data\":null,\"error\":\"" + escape(message) + "\",\"timestamp\":\"" + Instant.now() + "\"}");
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
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
