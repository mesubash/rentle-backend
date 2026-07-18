package com.rentle.domain.template.service;

import com.rentle.domain.listing.model.Category;
import com.rentle.domain.listing.repository.CategoryRepository;
import com.rentle.domain.template.model.CategoryFieldTemplate;
import com.rentle.domain.template.model.FieldDefinition;
import com.rentle.domain.template.model.FieldType;
import com.rentle.domain.template.model.TemplateScope;
import com.rentle.domain.template.repository.CategoryFieldTemplateRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The one template engine (docs/12 §3), used by all three scopes. Owns: reading the current
 * template for a category+scope, saving a new version (admin edits bump the version so existing
 * listings/bookings keep the version they answered), and validating an answer set against a
 * template. Pure logic — no scope-specific branches.
 */
@Service
public class FieldTemplateService {

    private final CategoryFieldTemplateRepository repository;
    private final CategoryRepository categoryRepository;

    public FieldTemplateService(CategoryFieldTemplateRepository repository, CategoryRepository categoryRepository) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<CategoryFieldTemplate> current(UUID categoryId, TemplateScope scope) {
        return repository.findTopByCategoryIdAndScopeOrderByVersionDesc(categoryId, scope);
    }

    @Transactional(readOnly = true)
    public List<CategoryFieldTemplate> forCategory(UUID categoryId) {
        return repository.findByCategoryIdOrderByScopeAscVersionDesc(categoryId);
    }

    /** Save a new version of a category+scope template (admin edit). */
    @Transactional
    public CategoryFieldTemplate saveNewVersion(UUID categoryId, TemplateScope scope,
                                                List<FieldDefinition> fields, UUID adminId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        validateDefinitions(fields);
        int nextVersion = current(categoryId, scope).map(t -> t.getVersion() + 1).orElse(1);
        CategoryFieldTemplate template = new CategoryFieldTemplate();
        template.setCategory(category);
        template.setScope(scope);
        template.setVersion(nextVersion);
        template.setFields(fields);
        template.setUpdatedBy(adminId);
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return repository.save(template);
    }

    /** The template definitions must themselves be well-formed before we accept them. */
    public void validateDefinitions(List<FieldDefinition> fields) {
        if (fields == null) throw new RentleException("Fields are required");
        for (FieldDefinition f : fields) {
            if (f.key() == null || f.key().isBlank()) throw new RentleException("Every field needs a key");
            if (!f.key().matches("[a-z0-9_]+")) throw new RentleException("Field key must be lowercase/underscore: " + f.key());
            if (f.label() == null || f.label().isBlank()) throw new RentleException("Field '" + f.key() + "' needs a label");
            if (f.type() == null) throw new RentleException("Field '" + f.key() + "' needs a type");
            if ((f.type() == FieldType.SELECT || f.type() == FieldType.MULTISELECT)
                    && (f.options() == null || f.options().isEmpty())) {
                throw new RentleException("Field '" + f.key() + "' needs options");
            }
        }
        long distinct = fields.stream().map(FieldDefinition::key).distinct().count();
        if (distinct != fields.size()) throw new RentleException("Field keys must be unique");
    }

    /**
     * Validate a submitted answer set against a template's fields. Returns nothing; throws
     * {@link RentleException} on the first problem. DOCUMENT fields are validated by presence of
     * their storage handling at the call site, not here (this only sees scalar answers).
     */
    public void validateAnswers(List<FieldDefinition> fields, Map<String, Object> answers) {
        Map<String, Object> a = answers == null ? Map.of() : answers;
        for (FieldDefinition f : fields) {
            Object value = a.get(f.key());
            boolean missing = value == null || (value instanceof String s && s.isBlank());
            if (missing) {
                if (f.required() && f.type() != FieldType.DOCUMENT && f.type() != FieldType.DOCUMENT_LIST) {
                    throw new RentleException(f.label() + " is required");
                }
                continue;
            }
            switch (f.type()) {
                case NUMBER -> {
                    if (!(value instanceof Number) && !isNumeric(value.toString())) {
                        throw new RentleException(f.label() + " must be a number");
                    }
                }
                case BOOLEAN -> {
                    String v = value.toString();
                    if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                        throw new RentleException(f.label() + " must be true or false");
                    }
                }
                case SELECT -> {
                    if (!f.options().contains(value.toString())) {
                        throw new RentleException(f.label() + " has an invalid choice");
                    }
                }
                case MULTISELECT -> {
                    if (!(value instanceof List<?> list)) {
                        throw new RentleException(f.label() + " must be a list of choices");
                    }
                    for (Object item : list) {
                        if (!f.options().contains(item.toString())) {
                            throw new RentleException(f.label() + " has an invalid choice");
                        }
                    }
                }
                default -> { /* TEXT/DATE/DOCUMENT: accept as-is */ }
            }
        }
    }

    private boolean isNumeric(String s) {
        try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
    }
}
