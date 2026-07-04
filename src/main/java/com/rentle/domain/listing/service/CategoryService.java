package com.rentle.domain.listing.service;

import com.rentle.domain.listing.dto.CategoryResponse;
import com.rentle.domain.listing.model.Category;
import com.rentle.domain.listing.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> tree() {
        List<Category> all = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
        Map<UUID, List<Category>> byParent = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return all.stream()
                .filter(c -> c.getParent() == null)
                .map(parent -> CategoryResponse.from(parent,
                        byParent.getOrDefault(parent.getId(), List.of()).stream()
                                .map(CategoryResponse::from)
                                .toList()))
                .toList();
    }
}
