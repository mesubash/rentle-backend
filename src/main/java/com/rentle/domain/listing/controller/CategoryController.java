package com.rentle.domain.listing.controller;

import com.rentle.domain.listing.dto.CategoryResponse;
import com.rentle.domain.listing.service.CategoryService;
import com.rentle.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> all() {
        return ApiResponse.ok(categoryService.listAll());
    }

    @GetMapping("/tree")
    public ApiResponse<List<CategoryResponse>> tree() {
        return ApiResponse.ok(categoryService.tree());
    }
}
