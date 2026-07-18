package com.rentle.domain.listing.service;

import com.rentle.domain.listing.dto.AdminCategoryRow;
import com.rentle.domain.listing.dto.CategoryResponse;
import com.rentle.domain.listing.model.Category;
import com.rentle.domain.listing.repository.CategoryRepository;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ListingRepository listingRepository;

    public CategoryService(CategoryRepository categoryRepository, ListingRepository listingRepository) {
        this.categoryRepository = categoryRepository;
        this.listingRepository = listingRepository;
    }

    /** All categories including hidden ones, for the admin console (docs/12). */
    @Transactional(readOnly = true)
    public List<AdminCategoryRow> listForAdmin() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(c -> AdminCategoryRow.from(c, listingRepository.countByCategoryId(c.getId())))
                .toList();
    }

    /** Launch or pause a category (interim is_active switch; docs/12 step 1). */
    @Transactional
    public AdminCategoryRow setActive(UUID id, boolean active) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        category.setIsActive(active);
        Category saved = categoryRepository.save(category);
        return AdminCategoryRow.from(saved, listingRepository.countByCategoryId(id));
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
