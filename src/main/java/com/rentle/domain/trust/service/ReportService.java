package com.rentle.domain.trust.service;

import com.rentle.domain.trust.dto.CreateReportRequest;
import com.rentle.domain.trust.dto.ReportResponse;
import com.rentle.domain.trust.model.Report;
import com.rentle.domain.trust.model.ReportStatus;
import com.rentle.domain.trust.repository.ReportRepository;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    /** Any authenticated user can file a report. */
    public ReportResponse create(UUID reporterId, CreateReportRequest req) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(req.targetType());
        report.setTargetId(req.targetId());
        report.setReason(req.reason());
        report.setStatus(ReportStatus.OPEN);
        return ReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> list(ReportStatus status, Pageable pageable) {
        var page = (status == null)
                ? reportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reportRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
        return PageResponse.from(page, ReportResponse::from);
    }

    /** Admin resolves or dismisses an open report. */
    public ReportResponse resolve(UUID adminId, UUID reportId, ReportStatus newStatus, String note) {
        if (newStatus != ReportStatus.RESOLVED && newStatus != ReportStatus.DISMISSED) {
            throw new RentleException("A report can only be resolved or dismissed");
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new RentleException("This report has already been handled");
        }
        report.setStatus(newStatus);
        report.setResolutionNote(note);
        report.setHandledBy(adminId);
        report.setHandledAt(Instant.now());
        return ReportResponse.from(reportRepository.save(report));
    }
}
