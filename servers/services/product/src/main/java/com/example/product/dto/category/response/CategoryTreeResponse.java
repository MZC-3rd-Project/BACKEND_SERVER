package com.example.product.dto.category.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.entity.category.Category;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class CategoryTreeResponse {

    @SnowflakeId
    private Long id;

    private String name;
    private Integer depth;
    private Integer sortOrder;

    @Builder.Default
    private List<CategoryTreeResponse> children = new ArrayList<>();

    public static CategoryTreeResponse from(Category entity) {
        return CategoryTreeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .depth(entity.getDepth())
                .sortOrder(entity.getSortOrder())
                .children(new ArrayList<>())
                .build();
    }
}
