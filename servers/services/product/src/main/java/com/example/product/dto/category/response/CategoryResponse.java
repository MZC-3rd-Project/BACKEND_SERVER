package com.example.product.dto.category.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.entity.category.Category;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CategoryResponse {

    @SnowflakeId
    private Long id;

    private String name;

    @SnowflakeId
    private Long parentId;

    private Integer depth;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CategoryResponse from(Category entity) {
        return CategoryResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .parentId(entity.getParentId())
                .depth(entity.getDepth())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
