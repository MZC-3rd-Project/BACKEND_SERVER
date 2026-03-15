package com.example.hotdeal.service.query;

import com.example.hotdeal.entity.HotDealStatus;

import java.time.LocalDateTime;

public record HotDealDetailView(
        Long id,
        Long itemId,
        String title,
        Long originalPrice,
        Integer discountRate,
        Long discountedPrice,
        Integer maxQuantity,
        Integer maxPerUser,
        Integer soldQuantity,
        Integer remainingQuantity,
        HotDealStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt
) {
}
