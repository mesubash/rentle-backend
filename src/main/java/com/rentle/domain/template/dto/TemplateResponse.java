package com.rentle.domain.template.dto;

import com.rentle.domain.template.model.CategoryFieldTemplate;
import com.rentle.domain.template.model.FieldDefinition;

import java.util.List;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        UUID categoryId,
        String scope,
        int version,
        List<FieldDefinition> fields
) {
    public static TemplateResponse from(CategoryFieldTemplate t) {
        return new TemplateResponse(t.getId(), t.getCategory().getId(), t.getScope().name(),
                t.getVersion(), t.getFields());
    }
}
