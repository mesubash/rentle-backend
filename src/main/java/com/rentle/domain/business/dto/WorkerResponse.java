package com.rentle.domain.business.dto;

import com.rentle.domain.business.model.Worker;

import java.util.UUID;

public record WorkerResponse(UUID id, String name, String phone, String role, boolean active) {
    public static WorkerResponse from(Worker w) {
        return new WorkerResponse(w.getId(), w.getName(), w.getPhone(), w.getRole(), w.isActive());
    }
}
