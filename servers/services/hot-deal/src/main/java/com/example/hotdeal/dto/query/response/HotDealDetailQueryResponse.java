package com.example.hotdeal.dto.query.response;

import com.example.core.id.jackson.SnowflakeId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
