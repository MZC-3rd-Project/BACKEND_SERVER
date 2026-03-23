package com.example.product.service.query.assembler;

import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.dto.performance.response.PerformanceListResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.performance.Performance;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PerformanceListAssembler {

    public PerformanceListResponse toResponse(Item item, Performance performance, List<ItemImage> images) {
        return PerformanceListResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .images(ItemImagesResponse.from(images, item.getThumbnailMediaId()))
                .averageRating(item.getAverageRating())
                .reviewCount(item.getReviewCount())
                .venue(performance.getVenue())
                .performanceDate(performance.getPerformanceDate())
                .performanceTime(performance.getPerformanceTime())
                .totalSeats(performance.getTotalSeats())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
