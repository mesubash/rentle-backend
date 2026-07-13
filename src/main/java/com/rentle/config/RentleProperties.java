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
        String sms,
        String appUrl,          // frontend base URL, for email verification links + OAuth return
        String apiBaseUrl,      // backend base URL, for the Google OAuth redirect URI
        String discordWebhook,  // dev delivery channel for OTPs and email links (no SMS/email provider yet)
        Iam iam
) {
    public record Iam(boolean enabled, boolean syncCatalog, String bootstrapSuperAdminEmail) {}
}
