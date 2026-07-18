package com.rentle.domain.business.controller;

import com.rentle.domain.business.dto.WorkerRequest;
import com.rentle.domain.business.dto.WorkerResponse;
import com.rentle.domain.business.service.WorkerService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The current business owner's worker registry (docs/07 Phase B). */
@RestController
@RequestMapping("/api/v1/users/me/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @GetMapping
    public ApiResponse<List<WorkerResponse>> list() {
        return ApiResponse.ok(workerService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkerResponse> add(@Valid @RequestBody WorkerRequest request) {
        return ApiResponse.ok(workerService.add(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkerResponse> update(@PathVariable UUID id, @Valid @RequestBody WorkerRequest request) {
        return ApiResponse.ok(workerService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> remove(@PathVariable UUID id) {
        workerService.remove(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("Worker removed");
    }
}
