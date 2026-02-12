package com.example.hotdeal.dto;

import com.example.core.id.jackson.SnowflakeId;
import com.example.hotdeal.entity.HotDeal;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HotDealListResponse {

    @SnowflakeId private Long id;
    private String title;
    private Integer discountRate;
    private Long discountedPrice;
    private Integer soldQuantity;
    private Integer maxQuantity;
    private LocalDateTime endAt;

    public static HotDealListResponse from(HotDeal h) {
        return HotDealListResponse.builder()
                .id(h.getId())
                .title(h.getTitle())
                .discountRate(h.getDiscountRate())
                .discountedPrice(h.getDiscountedPrice())
                .soldQuantity(h.getSoldQuantity())
                .maxQuantity(h.getMaxQuantity())
                .endAt(h.getEndAt())
                .build();
    }
}
