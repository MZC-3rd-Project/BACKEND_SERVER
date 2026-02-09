package com.example.product.dto.performance;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.domain.item.Item;
import com.example.product.domain.performance.Performance;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class PerformanceListResponse {

    @SnowflakeId
    private Long id;

    private String title;
    private Long price;
    private String status;
    private String thumbnailUrl;
    private String venue;
    private LocalDate performanceDate;
    private LocalTime performanceTime;
    private Integer totalSeats;
    private LocalDateTime createdAt;

    public static PerformanceListResponse of(Item item, Performance perf) {
        return PerformanceListResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .thumbnailUrl(item.getThumbnailUrl())
                .venue(perf.getVenue())
                .performanceDate(perf.getPerformanceDate())
                .performanceTime(perf.getPerformanceTime())
                .totalSeats(perf.getTotalSeats())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
