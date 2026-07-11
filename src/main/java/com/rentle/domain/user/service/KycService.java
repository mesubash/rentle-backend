package com.rentle.domain.user.service;

import com.rentle.domain.user.dto.KycResponse;
import com.rentle.domain.user.dto.KycSubmitRequest;
import com.rentle.domain.user.model.Address;
import com.rentle.domain.user.model.KycDetail;
import com.rentle.domain.user.model.KycStatus;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.KycDetailRepository;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.notification.SmsService;
import com.rentle.shared.storage.ImageValidator;
import com.rentle.shared.storage.PrivateStorageService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
public class KycService {

    private static final long DOC_MAX_BYTES = 5L * 1024 * 1024;

    private final KycDetailRepository kycRepository;
    private final UserRepository userRepository;
    private final PrivateStorageService privateStorage;
    private final SmsService smsService;

    public KycService(KycDetailRepository kycRepository,
                      UserRepository userRepository,
                      PrivateStorageService privateStorage,
                      SmsService smsService) {
        this.kycRepository = kycRepository;
        this.userRepository = userRepository;
        this.privateStorage = privateStorage;
        this.smsService = smsService;
    }

    public record KycImage(Resource resource, String contentType) {}

    @Transactional(readOnly = true)
    public KycResponse myKyc(UUID userId) {
        return kycRepository.findByUserId(userId).map(KycResponse::from).orElse(null);
    }

    /** Tier 1 submission — requires Tier 0 (verified phone + email) first. */
    @Transactional
    public KycResponse submit(UUID userId, KycSubmitRequest req, MultipartFile front, MultipartFile back) {
        User user = getUser(userId);
        if (!Boolean.TRUE.equals(user.getPhoneVerified()) || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new RentleException("Verify your phone and email before submitting identity details");
        }

        KycDetail kyc = kycRepository.findByUserId(userId).orElseGet(() -> {
            KycDetail k = new KycDetail();
            k.setUser(user);
            return k;
        });
        if (kyc.getStatus() == KycStatus.APPROVED) {
            throw new RentleException("Your identity is already verified");
        }

        ImageValidator.validate(front, DOC_MAX_BYTES);
        ImageValidator.validate(back, DOC_MAX_BYTES);
        kyc.setFrontImageRef(privateStorage.store(front, "kyc", userId + "-front"));
        kyc.setBackImageRef(privateStorage.store(back, "kyc", userId + "-back"));

        kyc.setRealName(req.getRealName().trim());
        kyc.setFatherName(req.getFatherName().trim());
        kyc.setGrandfatherName(req.getGrandfatherName().trim());
        kyc.setDateOfBirth(req.getDateOfBirth());
        kyc.setGender(req.getGender());
        kyc.setCitizenshipNumber(req.getCitizenshipNumber().trim());
        kyc.setCitizenshipIssueDistrict(req.getCitizenshipIssueDistrict().trim());
        kyc.setOccupation(req.getOccupation().trim());
        kyc.setPermanentAddress(new Address(
                req.getPermDistrict().trim(), req.getPermMunicipality().trim(), req.getPermWard(), trimOrNull(req.getPermTole())));
        kyc.setTemporaryAddress(new Address(
                req.getTempDistrict().trim(), req.getTempMunicipality().trim(), req.getTempWard(), trimOrNull(req.getTempTole())));

        // Resubmission after rejection returns to review.
        kyc.setStatus(KycStatus.SUBMITTED);
        kyc.setRejectionReason(null);
        kyc.setReviewedBy(null);
        kyc.setReviewedAt(null);
        return KycResponse.from(kycRepository.save(kyc));
    }

    // --- admin review ---

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<KycDetail> pending(Pageable pageable) {
        return kycRepository.findByStatusOrderByCreatedAtAsc(KycStatus.SUBMITTED, pageable);
    }

    @Transactional(readOnly = true)
    public KycResponse detail(UUID userId) {
        return KycResponse.from(getKyc(userId));
    }

    @Transactional(readOnly = true)
    public KycImage loadDocument(UUID userId, String side) {
        KycDetail kyc = getKyc(userId);
        String ref = "back".equalsIgnoreCase(side) ? kyc.getBackImageRef() : kyc.getFrontImageRef();
        return new KycImage(privateStorage.load(ref), privateStorage.contentType(ref));
    }

    /**
     * Approve KYC: the verified details become the account's record — the display
     * name is replaced with the real name (the signup name may have been informal
     * or wrong) and the KYC fields are henceforth immutable.
     */
    @Transactional
    public KycResponse approve(UUID adminId, UUID userId) {
        KycDetail kyc = getKyc(userId);
        if (kyc.getStatus() != KycStatus.SUBMITTED) {
            throw new RentleException("No submitted KYC to review for this user");
        }
        User user = kyc.getUser();
        if (!Boolean.TRUE.equals(user.getPhoneVerified()) || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new RentleException("User must have a verified phone and email first");
        }

        kyc.setStatus(KycStatus.APPROVED);
        kyc.setReviewedBy(adminId);
        kyc.setReviewedAt(Instant.now());
        kycRepository.save(kyc);

        user.setFullName(kyc.getRealName());   // authoritative, verified name
        user.setCitizenshipVerified(true);
        user.setStatus(UserStatus.VERIFIED);
        userRepository.save(user);

        smsService.send(user.getPhoneNumber(),
                "Your Rentle identity is verified. You can now book and list.");
        return KycResponse.from(kyc);
    }

    @Transactional
    public KycResponse reject(UUID adminId, UUID userId, String reason) {
        KycDetail kyc = getKyc(userId);
        if (kyc.getStatus() != KycStatus.SUBMITTED) {
            throw new RentleException("No submitted KYC to review for this user");
        }
        kyc.setStatus(KycStatus.REJECTED);
        kyc.setRejectionReason(reason != null && !reason.isBlank() ? reason : "Details could not be verified");
        kyc.setReviewedBy(adminId);
        kyc.setReviewedAt(Instant.now());
        return KycResponse.from(kycRepository.save(kyc));
    }

    private String trimOrNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private KycDetail getKyc(UUID userId) {
        return kycRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No KYC on file for this user"));
    }
}
