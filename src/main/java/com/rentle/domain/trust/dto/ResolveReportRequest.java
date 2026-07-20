package com.rentle.domain.trust.dto;

import com.rentle.domain.trust.model.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
        @NotNull ReportStatus status,   // RESOLVED or DISMISSED
        @Size(max = 1000) String note
) {}
