package com.rentle.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 100) String fullName,
        @Email @Size(max = 100) String email,
        @Size(max = 100) String paymentWallet,
        String accountType,          // INDIVIDUAL | BUSINESS
        @Size(max = 120) String businessName
) {}
