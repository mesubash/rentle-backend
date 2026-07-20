package com.rentle.domain.template.repository;

import com.rentle.domain.template.model.CategoryFieldTemplate;
import com.rentle.domain.template.model.TemplateScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryFieldTemplateRepository extends JpaRepository<CategoryFieldTemplate, UUID> {

    /** The current (highest-version) template for a category+scope. */
    Optional<CategoryFieldTemplate> findTopByCategoryIdAndScopeOrderByVersionDesc(UUID categoryId, TemplateScope scope);

    List<CategoryFieldTemplate> findByCategoryIdOrderByScopeAscVersionDesc(UUID categoryId);
}
