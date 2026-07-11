package com.rentle.domain.user.dto;

/** Returned by POST /auth/register: the account is not created until the phone OTP is verified. */
public record RegistrationResponse(
        String phoneNumber,
        boolean otpRequired,
        long expiresInSeconds,
        String message
) {
    public static RegistrationResponse otpSent(String phoneNumber, long ttlSeconds) {
        return new RegistrationResponse(phoneNumber, true, ttlSeconds,
                "We sent a verification code to your phone. Enter it to finish creating your account.");
    }
}
