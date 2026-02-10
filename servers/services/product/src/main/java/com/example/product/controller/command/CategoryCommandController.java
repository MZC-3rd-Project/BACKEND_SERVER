package com.example.product.controller.command;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.command.CategoryCommandApi;
import com.example.product.dto.category.request.CategoryCreateRequest;
import com.example.product.dto.category.request.CategoryUpdateRequest;
import com.example.product.dto.category.response.CategoryResponse;
import com.example.product.service.command.CategoryCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryCommandController implements CategoryCommandApi {

    private final CategoryCommandService categoryCommandService;

    @Override
    public ApiResponse<CategoryResponse> create(CategoryCreateRequest request) {
        return ApiResponse.success(CategoryResponse.from(categoryCommandService.create(request)));
    }

    @Override
    public ApiResponse<CategoryResponse> update(Long id, CategoryUpdateRequest request) {
        return ApiResponse.success(CategoryResponse.from(categoryCommandService.update(id, request)));
    }

    @Override
    public ApiResponse<Void> delete(Long id) {
        categoryCommandService.delete(id);
        return ApiResponse.success();
    }
}
