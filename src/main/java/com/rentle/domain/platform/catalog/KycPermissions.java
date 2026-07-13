package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KycPermissions implements PermissionCatalog {

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of(
                new PermissionDefinition(PermissionKeys.KYC_SUBMISSION_READ, "kyc", "submission", "read", "View KYC submissions and documents"),
                new PermissionDefinition(PermissionKeys.KYC_SUBMISSION_APPROVE, "kyc", "submission", "approve", "Approve a KYC submission"),
                new PermissionDefinition(PermissionKeys.KYC_SUBMISSION_REJECT, "kyc", "submission", "reject", "Reject a KYC submission")
        );
    }
}
