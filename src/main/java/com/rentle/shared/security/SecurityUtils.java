package com.rentle.shared.security;

import com.rentle.shared.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID currentUserId() {
        return UUID.fromString(currentJwt().getSubject());
    }

    /** Null when the request is anonymous (public endpoints). */
    public static UUID currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return UUID.fromString(jwt.getSubject());
    }

    /** True when the current authentication holds the given permission authority. */
    public static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    public static Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("Not authenticated");
        }
        return jwt;
    }
}
