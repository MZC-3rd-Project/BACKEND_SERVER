package com.example.product.dto.item.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ReviewMetricsUpdateRequest {

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal averageRating;

    @NotNull
    @Min(0)
    private Long reviewCount;
}
