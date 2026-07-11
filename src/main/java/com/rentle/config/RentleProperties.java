package com.rentle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rentle")
public record RentleProperties(
        int platformFeePercent,
        int reviewWindowDays,
        int otpExpiryMinutes,
        int maxListingImages,
        String storage,
        String localUploadDir,
        String privateUploadDir,
        String sms
) {}
