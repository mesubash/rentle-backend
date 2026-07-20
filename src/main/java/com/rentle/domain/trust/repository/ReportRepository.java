package com.rentle.domain.trust.repository;

import com.rentle.domain.trust.model.Report;
import com.rentle.domain.trust.model.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
