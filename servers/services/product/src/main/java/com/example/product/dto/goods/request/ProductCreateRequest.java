package com.example.product.dto.goods.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "상품 제목은 필수입니다")
    @Size(max = 200)
    private String title;

    private String description;

    @NotNull(message = "가격은 필수입니다")
    @Min(value = 0)
    private Long price;

    private Long categoryId;

    private String thumbnailUrl;

    @Valid
    private List<ItemOptionRequest> options;

    @NotNull(message = "배송 정보는 필수입니다")
    @Valid
    private ShippingInfoRequest shippingInfo;
}
