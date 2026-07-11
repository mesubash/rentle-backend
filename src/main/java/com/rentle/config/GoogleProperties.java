package com.rentle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record GoogleProperties(String clientId) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }
}
