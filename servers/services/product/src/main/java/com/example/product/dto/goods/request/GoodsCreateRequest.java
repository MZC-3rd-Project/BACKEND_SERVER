package com.example.product.dto.goods.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GoodsCreateRequest {

    @NotBlank(message = "굿즈 제목은 필수입니다")
    @Size(max = 200)
    private String title;

    private String description;

    @NotNull(message = "가격은 필수입니다")
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
