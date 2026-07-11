package com.rentle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record GoogleProperties(String clientId, String clientSecret, String redirectUri) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    /** Backend-driven code flow additionally needs the secret. */
    public boolean isOAuthConfigured() {
        return isConfigured() && clientSecret != null && !clientSecret.isBlank();
    }
}
