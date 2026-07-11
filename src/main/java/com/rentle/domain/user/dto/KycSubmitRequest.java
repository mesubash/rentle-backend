package com.rentle.domain.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Multipart form fields for a KYC submission (front/back images are separate parts). */
@Getter
@Setter
public class KycSubmitRequest {

    @NotBlank @Size(max = 120)
    private String realName;

    @NotBlank @Size(max = 120)
    private String fatherName;

    @NotBlank @Size(max = 120)
    private String grandfatherName;

    @NotNull @Past
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @Size(max = 10)
    private String gender;

    @NotBlank @Size(max = 40)
    private String citizenshipNumber;

    @NotBlank @Size(max = 60)
    private String citizenshipIssueDistrict;

    @NotBlank @Size(max = 80)
    private String occupation;

    @NotBlank @Size(max = 60)
    private String permDistrict;

    @NotBlank @Size(max = 80)
    private String permMunicipality;

    @NotNull @Min(1) @Max(35)
    private Integer permWard;

    @Size(max = 120)
    private String permTole;

    @NotBlank @Size(max = 60)
    private String tempDistrict;

    @NotBlank @Size(max = 80)
    private String tempMunicipality;

    @NotNull @Min(1) @Max(35)
    private Integer tempWard;

    @Size(max = 120)
    private String tempTole;
}
