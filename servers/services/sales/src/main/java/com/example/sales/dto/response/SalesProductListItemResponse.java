package com.example.sales.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SalesProductListItemResponse {

    @SnowflakeId
    private Long id;
    private String title;
    private String fundingTitle;
    private String storeName;
    private String category;
    private String thumbnailUrl;
    @SnowflakeId
    private Long thumbnailMediaId;
    private Long price;
    private Integer stockLeft;
    private Long soldCount;
    private String status;
}
