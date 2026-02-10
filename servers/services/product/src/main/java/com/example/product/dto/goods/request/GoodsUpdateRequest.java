package com.example.product.dto.goods.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GoodsUpdateRequest {

    @Size(max = 200)
    private String title;

    private String description;

    @Min(value = 0)
    private Long price;

    private Long categoryId;

    private String thumbnailUrl;

    private List<Long> linkedPerformanceItemIds;

    @Valid
    private List<ItemOptionRequest> options;

    @Valid
    private ShippingInfoRequest shippingInfo;
}
