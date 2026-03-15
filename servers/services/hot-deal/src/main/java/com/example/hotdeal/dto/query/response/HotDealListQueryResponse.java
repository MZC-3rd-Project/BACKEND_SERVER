package com.example.hotdeal.dto.query.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HotDealListQueryResponse {

    @SnowflakeId
    private Long id;

    private String title;
    private Integer discountRate;
    private Long discountedPrice;
    private Integer soldQuantity;
    private Integer maxQuantity;
    private LocalDateTime endAt;
}
