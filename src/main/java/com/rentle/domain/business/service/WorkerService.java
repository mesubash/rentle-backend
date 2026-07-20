package com.rentle.domain.business.service;

import com.rentle.domain.business.dto.WorkerRequest;
import com.rentle.domain.business.dto.WorkerResponse;
import com.rentle.domain.business.model.Worker;
import com.rentle.domain.business.repository.WorkerRepository;
import com.rentle.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkerService {

    private final WorkerRepository repository;

    public WorkerService(WorkerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WorkerResponse> list(UUID businessId) {
        return repository.findByBusinessIdOrderByCreatedAtAsc(businessId).stream().map(WorkerResponse::from).toList();
    }

    @Transactional
    public WorkerResponse add(UUID businessId, WorkerRequest req) {
        Worker w = new Worker();
        w.setBusinessId(businessId);
        w.setName(req.name().trim());
        w.setPhone(req.phone());
        w.setRole(req.role());
        return WorkerResponse.from(repository.save(w));
    }

    @Transactional
    public WorkerResponse update(UUID businessId, UUID workerId, WorkerRequest req) {
        Worker w = owned(businessId, workerId);
        w.setName(req.name().trim());
        w.setPhone(req.phone());
        w.setRole(req.role());
        return WorkerResponse.from(repository.save(w));
    }

    @Transactional
    public void remove(UUID businessId, UUID workerId) {
        Worker w = owned(businessId, workerId);
        w.setActive(false);   // soft-remove: keeps historical booking assignments intact
        repository.save(w);
    }

    private Worker owned(UUID businessId, UUID workerId) {
        return repository.findByIdAndBusinessId(workerId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found"));
    }
}
