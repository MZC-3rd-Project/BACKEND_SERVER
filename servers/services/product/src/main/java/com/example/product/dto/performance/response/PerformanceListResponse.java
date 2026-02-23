package com.example.product.dto.performance.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.performance.Performance;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class PerformanceListResponse {

    @SnowflakeId
    private Long id;

    private String title;
    private Long price;
    private String status;
    private ItemImagesResponse images;
    private String venue;
    private LocalDate performanceDate;
    private LocalTime performanceTime;
    private Integer totalSeats;
    private LocalDateTime createdAt;

    public static PerformanceListResponse of(Item item, Performance perf, List<ItemImage> images) {
        return PerformanceListResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .images(ItemImagesResponse.from(images, item.getThumbnailMediaId()))
                .venue(perf.getVenue())
                .performanceDate(perf.getPerformanceDate())
                .performanceTime(perf.getPerformanceTime())
                .totalSeats(perf.getTotalSeats())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
