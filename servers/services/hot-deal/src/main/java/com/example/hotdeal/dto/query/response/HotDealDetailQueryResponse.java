package com.example.hotdeal.dto.query.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HotDealDetailQueryResponse {

    @SnowflakeId
    private Long id;

    @SnowflakeId
    private Long itemId;

    private String title;
    private Long originalPrice;
    private Integer discountRate;
    private Long discountedPrice;
    private Integer maxQuantity;
    private Integer maxPerUser;
    private Integer soldQuantity;
    private Integer remainingQuantity;
    private Double progressRate;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;
}
