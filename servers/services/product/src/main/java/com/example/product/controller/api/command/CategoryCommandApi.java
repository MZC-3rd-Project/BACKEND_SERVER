package com.example.product.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.product.dto.category.request.CategoryCreateRequest;
import com.example.product.dto.category.request.CategoryUpdateRequest;
import com.example.product.dto.category.response.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Category Command", description = "카테고리 관리 API (쓰기)")
public interface CategoryCommandApi {

    @Operation(summary = "카테고리 생성")
    @PostMapping
    ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request);

    @Operation(summary = "카테고리 수정")
    @PutMapping("/{id}")
    ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request);

    @Operation(summary = "카테고리 삭제")
    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable Long id);
}
