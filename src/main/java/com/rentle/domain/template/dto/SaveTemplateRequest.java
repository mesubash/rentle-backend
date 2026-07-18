package com.rentle.domain.template.dto;

import com.rentle.domain.template.model.FieldDefinition;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveTemplateRequest(@NotNull List<FieldDefinition> fields) {}
