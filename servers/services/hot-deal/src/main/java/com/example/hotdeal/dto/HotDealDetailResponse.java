package com.example.hotdeal.dto;

import com.example.core.id.jackson.SnowflakeId;
import com.example.hotdeal.entity.HotDeal;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HotDealDetailResponse {

    @SnowflakeId private Long id;
    @SnowflakeId private Long itemId;
    private String title;
    private Long originalPrice;
    private Integer discountRate;
    private Long discountedPrice;
    private Integer maxQuantity;
    private Integer soldQuantity;
    private Integer remainingQuantity;
    private Double progressRate;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;

    public static HotDealDetailResponse from(HotDeal h) {
        double progress = h.getMaxQuantity() > 0
                ? (double) h.getSoldQuantity() / h.getMaxQuantity() * 100
                : 0.0;

        return HotDealDetailResponse.builder()
                .id(h.getId())
                .itemId(h.getItemId())
                .title(h.getTitle())
                .originalPrice(h.getOriginalPrice())
                .discountRate(h.getDiscountRate())
                .discountedPrice(h.getDiscountedPrice())
                .maxQuantity(h.getMaxQuantity())
                .soldQuantity(h.getSoldQuantity())
                .remainingQuantity(h.getRemainingQuantity())
                .progressRate(Math.round(progress * 10.0) / 10.0)
                .status(h.getStatus().name())
                .startAt(h.getStartAt())
                .endAt(h.getEndAt())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
