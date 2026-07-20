package com.rentle.domain.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkerRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 20) String phone,
        @Size(max = 80) String role
) {}
