package com.example.product.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.product.dto.category.response.CategoryResponse;
import com.example.product.dto.category.response.CategoryTreeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Category Query", description = "카테고리 조회 API (읽기)")
public interface CategoryQueryApi {

    @Operation(summary = "카테고리 단건 조회")
    @GetMapping("/{id}")
    ApiResponse<CategoryResponse> findById(@PathVariable Long id);

    @Operation(summary = "카테고리 트리 조회")
    @GetMapping("/tree")
    ApiResponse<List<CategoryTreeResponse>> getTree();
}
