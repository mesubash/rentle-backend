package com.rentle.domain.template.model;

import java.util.List;

/**
 * One required/optional item in a category template (docs/12 §3.1). Serialized as JSON inside
 * the template's {@code fields} array. {@code options} applies to SELECT/MULTISELECT.
 */
public record FieldDefinition(
        String key,
        String label,
        FieldType type,
        boolean required,
        List<String> options,
        String help
) {}
