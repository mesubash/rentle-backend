package com.rentle.domain.verification.service;

import com.rentle.domain.notification.service.NotificationService;
import com.rentle.domain.template.model.TemplateScope;
import com.rentle.domain.template.service.FieldTemplateService;
import com.rentle.domain.verification.dto.ProviderVerificationResponse;
import com.rentle.domain.verification.dto.SubmitVerificationRequest;
import com.rentle.domain.verification.model.ProviderVerification;
import com.rentle.domain.verification.repository.ProviderVerificationRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Provider verification (docs/07 Phase A). A category "requires provider verification" when it has a
 * VERIFICATION-scope field template. Providers submit that template's answers; an admin approves;
 * an approved verification is required to publish a SERVICE listing in that category.
 */
@Service
public class ProviderVerificationService {

    private final ProviderVerificationRepository repository;
    private final FieldTemplateService templateService;
    private final NotificationService notificationService;

    public ProviderVerificationService(ProviderVerificationRepository repository,
                                       FieldTemplateService templateService,
                                       NotificationService notificationService) {
        this.repository = repository;
        this.templateService = templateService;
        this.notificationService = notificationService;
    }

    /** Does this category gate publishing (i.e. has a verification template with any field)? */
    @Transactional(readOnly = true)
    public boolean categoryRequiresVerification(UUID categoryId) {
        return templateService.current(categoryId, TemplateScope.VERIFICATION)
                .map(t -> !t.getFields().isEmpty()).orElse(false);
    }

    /** True if the user may publish in this category (no template, or an APPROVED submission). */
    @Transactional(readOnly = true)
    public boolean isVerifiedFor(UUID userId, UUID categoryId) {
        if (!categoryRequiresVerification(categoryId)) return true;
        return repository.existsByUserIdAndCategoryIdAndStatus(userId, categoryId, "APPROVED");
    }

    @Transactional
    public ProviderVerificationResponse submit(UUID userId, SubmitVerificationRequest req) {
        var template = templateService.current(req.categoryId(), TemplateScope.VERIFICATION)
                .orElseThrow(() -> new RentleException("This category does not require provider verification"));
        var answers = req.fields() != null ? req.fields() : new java.util.HashMap<String, Object>();
        templateService.validateAnswers(template.getFields(), answers);

        ProviderVerification v = repository.findByUserIdAndCategoryId(userId, req.categoryId())
                .orElseGet(() -> {
                    ProviderVerification n = new ProviderVerification();
                    n.setUserId(userId);
                    n.setCategoryId(req.categoryId());
                    return n;
                });
        v.setFields(answers);
        v.setTemplateVersion(template.getVersion());
        v.setStatus("SUBMITTED");         // resubmission re-enters the queue
        v.setRejectionReason(null);
        v.setUpdatedAt(Instant.now());
        return ProviderVerificationResponse.from(repository.save(v));
    }

    @Transactional(readOnly = true)
    public List<ProviderVerificationResponse> mine(UUID userId) {
        return repository.findByUserId(userId).stream().map(ProviderVerificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProviderVerificationResponse> queue(String status, Pageable pageable) {
        return PageResponse.from(
                repository.findByStatusOrderByCreatedAtAsc(status == null ? "SUBMITTED" : status, pageable),
                ProviderVerificationResponse::from);
    }

    @Transactional
    public ProviderVerificationResponse decide(UUID adminId, UUID id, boolean approve, String reason) {
        ProviderVerification v = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found"));
        v.setStatus(approve ? "APPROVED" : "REJECTED");
        v.setRejectionReason(approve ? null : (reason != null ? reason : "Credentials could not be verified"));
        v.setReviewedBy(adminId);
        v.setReviewedAt(Instant.now());
        v.setUpdatedAt(Instant.now());
        ProviderVerificationResponse saved = ProviderVerificationResponse.from(repository.save(v));
        notificationService.notify(v.getUserId(), approve ? "PROVIDER_APPROVED" : "PROVIDER_REJECTED",
                approve ? "Your provider verification was approved — you can now list in that category."
                        : "Your provider verification needs attention: " + v.getRejectionReason(),
                "/verification");
        return saved;
    }
}
