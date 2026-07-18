package com.rentle.domain.business.repository;

import com.rentle.domain.business.model.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    List<Worker> findByBusinessIdOrderByCreatedAtAsc(UUID businessId);

    Optional<Worker> findByIdAndBusinessId(UUID id, UUID businessId);
}
