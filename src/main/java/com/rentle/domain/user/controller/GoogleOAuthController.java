package com.rentle.domain.user.controller;

import com.rentle.config.RentleProperties;
import com.rentle.domain.user.dto.AuthResponse;
import com.rentle.domain.user.dto.GoogleExchangeRequest;
import com.rentle.domain.user.service.GoogleOAuthService;
import com.rentle.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleOAuthController {

    private final GoogleOAuthService oauth;
    private final RentleProperties props;

    public GoogleOAuthController(GoogleOAuthService oauth, RentleProperties props) {
        this.oauth = oauth;
        this.props = props;
    }

    /** Whether Google sign-in is available, and the URL the button should link to. */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "enabled", oauth.isEnabled(),
                "loginUrl", props.apiBaseUrl() + "/api/v1/auth/google/login"));
    }

    /** Full-page entry point: redirects the browser to Google's consent screen. */
    @GetMapping("/login")
    public RedirectView login() {
        try {
            return new RedirectView(oauth.startUrl());
        } catch (RuntimeException ex) {
            return new RedirectView(props.appUrl() + "/auth/login?error=google_unavailable");
        }
    }

    /** Google returns here; on success redirect to the app with a one-time handoff code. */
    @GetMapping("/callback")
    public RedirectView callback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error) {
        if (error != null || code == null) {
            return new RedirectView(props.appUrl() + "/auth/login?error=google_denied");
        }
        try {
            return new RedirectView(oauth.handleCallback(code, state));
        } catch (RuntimeException ex) {
            return new RedirectView(props.appUrl() + "/auth/login?error=google_failed");
        }
    }

    /** The app swaps the handoff code for a session (BFF stores the cookies). */
    @PostMapping("/exchange")
    public ApiResponse<AuthResponse> exchange(@Valid @RequestBody GoogleExchangeRequest request) {
        return ApiResponse.ok(oauth.exchangeHandoff(request.code()));
    }
}
