package com.rentle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        long accessTokenExpiryMs,
        long refreshTokenExpiryMs
) {}
