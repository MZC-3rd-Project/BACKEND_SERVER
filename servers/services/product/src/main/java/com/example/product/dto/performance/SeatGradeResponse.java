package com.example.product.dto.performance;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.domain.performance.SeatGrade;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeatGradeResponse {

    @SnowflakeId
    private Long id;

    private String gradeName;
    private Long price;
    private Integer totalQuantity;
    private Integer fundingQuantity;

    public static SeatGradeResponse from(SeatGrade entity) {
        return SeatGradeResponse.builder()
                .id(entity.getId())
                .gradeName(entity.getGradeName())
                .price(entity.getPrice())
                .totalQuantity(entity.getTotalQuantity())
                .fundingQuantity(entity.getFundingQuantity())
                .build();
    }
}
