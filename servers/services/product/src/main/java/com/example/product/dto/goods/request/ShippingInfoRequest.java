package com.example.product.dto.goods.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShippingInfoRequest {

    @NotNull(message = "배송비는 필수입니다")
    @Min(value = 0, message = "배송비는 0 이상이어야 합니다")
    private Long shippingFee;

    private Long freeShippingThreshold;

    @Min(value = 1, message = "예상 배송일은 1일 이상이어야 합니다")
    private int estimatedDays;

    private String returnPolicy;

    @Size(max = 100, message = "택배사는 100자 이하여야 합니다")
    @Schema(description = "택배사", example = "CJ대한통운")
    private String carrier;

    @Size(max = 255, message = "출고지는 255자 이하여야 합니다")
    @Schema(description = "출고지", example = "서울특별시 마포구")
    private String shipFrom;

    @Size(max = 255, message = "반품 주소는 255자 이하여야 합니다")
    @Schema(description = "반품 주소", example = "인천광역시 서구")
    private String returnAddress;

    @Min(value = 0, message = "반품 배송비는 0 이상이어야 합니다")
    @Schema(description = "반품 배송비", example = "3500")
    private Long returnShippingFee;

    @Min(value = 0, message = "교환 배송비는 0 이상이어야 합니다")
    @Schema(description = "교환 배송비", example = "7000")
    private Long exchangeShippingFee;

    @Schema(description = "배송 관련 안내", example = "도서산간 지역은 추가 배송비가 발생할 수 있습니다.")
    private String shippingNotice;
}
