package com.example.product.dto.goods.request;

import com.example.product.dto.item.request.ItemDetailSectionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ProductUpdateRequest {

    @Size(max = 200)
    private String title;

    private String description;

    @Min(value = 0)
    private Long price;

    private Long categoryId;

    @Positive(message = "썸네일 미디어 ID는 양수여야 합니다")
    private Long thumbnailMediaId;

    private Boolean clearThumbnail;

    private List<@Size(max = 50) String> tags;

    private List<@Size(max = 200) String> features;

    @Valid
    private List<ItemDetailSectionRequest> detailSections;

    @Valid
    private List<ItemOptionRequest> options;

    @Valid
    private ShippingInfoRequest shippingInfo;
}
