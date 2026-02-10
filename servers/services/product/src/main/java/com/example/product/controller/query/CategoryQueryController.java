package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.query.CategoryQueryApi;
import com.example.product.dto.category.response.CategoryResponse;
import com.example.product.dto.category.response.CategoryTreeResponse;
import com.example.product.service.query.CategoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryQueryController implements CategoryQueryApi {

    private final CategoryQueryService categoryQueryService;

    @Override
    public ApiResponse<CategoryResponse> findById(Long id) {
        return ApiResponse.success(CategoryResponse.from(categoryQueryService.findById(id)));
    }

    @Override
    public ApiResponse<List<CategoryTreeResponse>> getTree() {
        return ApiResponse.success(categoryQueryService.getTree());
    }
}
