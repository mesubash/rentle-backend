package com.rentle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "rentle")
public record RentleProperties(
        int platformFeePercent,
        int reviewWindowDays,
        int otpExpiryMinutes,
        int maxListingImages,
        String storage,
        String localUploadDir,
        String sms,
        List<String> corsAllowedOrigins
) {}
