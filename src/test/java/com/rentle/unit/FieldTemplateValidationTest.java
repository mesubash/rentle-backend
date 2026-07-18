package com.rentle.unit;

import com.rentle.domain.template.model.FieldDefinition;
import com.rentle.domain.template.model.FieldType;
import com.rentle.domain.template.service.FieldTemplateService;
import com.rentle.shared.exception.RentleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Template validation is the reusable core of the field-template engine (docs/12). */
class FieldTemplateValidationTest {

    // No JPA needed — validation is pure logic.
    private final FieldTemplateService svc = new FieldTemplateService(null, null);

    private final List<FieldDefinition> template = List.of(
            new FieldDefinition("size", "Size", FieldType.SELECT, true, List.of("S", "M", "L"), null),
            new FieldDefinition("chest_cm", "Chest (cm)", FieldType.NUMBER, false, null, null),
            new FieldDefinition("dry_cleaned", "Dry cleaned", FieldType.BOOLEAN, false, null, null));

    @Test
    void acceptsValidAnswers() {
        assertDoesNotThrow(() -> svc.validateAnswers(template,
                Map.of("size", "M", "chest_cm", "96", "dry_cleaned", "true")));
    }

    @Test
    void rejectsMissingRequired() {
        assertThrows(RentleException.class, () ->
                svc.validateAnswers(template, Map.of("chest_cm", "96")));
    }

    @Test
    void rejectsBadSelectChoice() {
        assertThrows(RentleException.class, () ->
                svc.validateAnswers(template, Map.of("size", "XXL")));
    }

    @Test
    void rejectsNonNumeric() {
        assertThrows(RentleException.class, () ->
                svc.validateAnswers(template, Map.of("size", "M", "chest_cm", "big")));
    }

    @Test
    void rejectsDuplicateKeysInDefinition() {
        assertThrows(RentleException.class, () -> svc.validateDefinitions(List.of(
                new FieldDefinition("k", "A", FieldType.TEXT, true, null, null),
                new FieldDefinition("k", "B", FieldType.TEXT, false, null, null))));
    }

    @Test
    void rejectsSelectWithoutOptions() {
        assertThrows(RentleException.class, () -> svc.validateDefinitions(List.of(
                new FieldDefinition("k", "A", FieldType.SELECT, true, List.of(), null))));
    }
}
