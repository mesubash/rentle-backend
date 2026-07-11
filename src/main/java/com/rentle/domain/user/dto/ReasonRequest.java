package com.rentle.domain.user.dto;

import jakarta.validation.constraints.Size;

public record ReasonRequest(
        @Size(max = 400) String reason
) {}
