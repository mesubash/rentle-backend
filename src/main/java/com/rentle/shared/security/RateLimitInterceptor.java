package com.rentle.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Redis-backed fixed-window rate limiting per client IP.
 * 60 rpm on all API routes; 5 login attempts per 15 minutes.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final int DEFAULT_RPM = 60;
    static final int LOGIN_PER_15_MIN = 5;

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String ip = clientIp(request);

        if (!rateLimitService.allow("ip:" + ip, DEFAULT_RPM, Duration.ofMinutes(1))) {
            response.sendError(429, "Too many requests");
            return false;
        }

        if ("POST".equals(request.getMethod()) && request.getRequestURI().endsWith("/auth/login")) {
            if (!rateLimitService.allow("login:" + ip, LOGIN_PER_15_MIN, Duration.ofMinutes(15))) {
                response.sendError(429, "Too many login attempts, try again later");
                return false;
            }
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
