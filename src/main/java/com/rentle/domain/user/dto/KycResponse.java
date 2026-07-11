package com.rentle.domain.user.dto;

import com.rentle.domain.user.model.Address;
import com.rentle.domain.user.model.KycDetail;

import java.time.Instant;
import java.time.LocalDate;

/**
 * KYC as shown to the owner (and, with images, to admins). Once APPROVED these
 * values are the verified record and are no longer editable.
 */
public record KycResponse(
        String status,
        String realName,
        String fatherName,
        String grandfatherName,
        LocalDate dateOfBirth,
        String gender,
        String citizenshipNumber,
        String citizenshipIssueDistrict,
        String occupation,
        AddressDto permanentAddress,
        AddressDto temporaryAddress,
        String rejectionReason,
        Instant reviewedAt,
        Instant submittedAt
) {
    public record AddressDto(String district, String municipality, Integer ward, String tole) {
        static AddressDto from(Address a) {
            return a == null ? null : new AddressDto(a.getDistrict(), a.getMunicipality(), a.getWard(), a.getTole());
        }
    }

    public static KycResponse from(KycDetail k) {
        return new KycResponse(
                k.getStatus().name(),
                k.getRealName(),
                k.getFatherName(),
                k.getGrandfatherName(),
                k.getDateOfBirth(),
                k.getGender(),
                k.getCitizenshipNumber(),
                k.getCitizenshipIssueDistrict(),
                k.getOccupation(),
                AddressDto.from(k.getPermanentAddress()),
                AddressDto.from(k.getTemporaryAddress()),
                k.getRejectionReason(),
                k.getReviewedAt(),
                k.getCreatedAt()
        );
    }
}
