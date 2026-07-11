package com.rentle.shared.security;

import com.rentle.shared.api.JsonErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Redis-backed fixed-window request throttling. Keyed by authenticated user id
 * when present, else client IP — so that a same-origin BFF (all traffic from one
 * host IP) does not turn the per-IP cap into a global lockout. Auth-endpoint
 * limits (login attempts, OTP) are account-scoped inside the services.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final int DEFAULT_RPM = 120;

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String key = callerKey(request);
        if (!rateLimitService.allow("req:" + key, DEFAULT_RPM, Duration.ofMinutes(1))) {
            JsonErrorWriter.write(response, 429, "Too many requests. Please slow down.");
            return false;
        }
        return true;
    }

    /** Authenticated user id when available, else the client IP. */
    private String callerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return "u:" + jwt.getSubject();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
