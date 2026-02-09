package com.example.product.controller;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.CategoryApi;
import com.example.product.dto.category.*;
import com.example.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController implements CategoryApi {

    private final CategoryService categoryService;

    @Override
    public ApiResponse<CategoryResponse> create(CategoryCreateRequest request) {
        return ApiResponse.success(CategoryResponse.from(categoryService.create(request)));
    }

    @Override
    public ApiResponse<CategoryResponse> findById(Long id) {
        return ApiResponse.success(CategoryResponse.from(categoryService.findById(id)));
    }

    @Override
    public ApiResponse<CategoryResponse> update(Long id, CategoryUpdateRequest request) {
        return ApiResponse.success(CategoryResponse.from(categoryService.update(id, request)));
    }

    @Override
    public ApiResponse<Void> delete(Long id) {
        categoryService.delete(id);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<List<CategoryTreeResponse>> getTree() {
        return ApiResponse.success(categoryService.getTree());
    }
}
