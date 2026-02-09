package com.example.product.controller.api;

import com.example.api.response.ApiResponse;
import com.example.product.dto.category.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Category", description = "카테고리 관리 API")
public interface CategoryApi {

    @Operation(summary = "카테고리 생성", description = "루트 또는 하위 카테고리 생성. parentId가 null이면 루트 카테고리")
    @PostMapping
    ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request);

    @Operation(summary = "카테고리 단건 조회")
    @GetMapping("/{id}")
    ApiResponse<CategoryResponse> findById(@PathVariable Long id);

    @Operation(summary = "카테고리 수정")
    @PutMapping("/{id}")
    ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request);

    @Operation(summary = "카테고리 삭제", description = "하위 카테고리가 있으면 삭제 불가")
    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable Long id);

    @Operation(summary = "카테고리 트리 조회", description = "전체 카테고리를 트리 구조로 반환")
    @GetMapping("/tree")
    ApiResponse<List<CategoryTreeResponse>> getTree();
}
