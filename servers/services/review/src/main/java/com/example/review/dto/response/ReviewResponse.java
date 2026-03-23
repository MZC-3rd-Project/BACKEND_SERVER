package com.example.review.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.review.domain.Review;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ReviewResponse {

    @SnowflakeId
    private Long id;

    @SnowflakeId
    private Long orderId;

    @SnowflakeId
    private Long itemId;

    @SnowflakeId
    private Long userId;

    private Integer rating;
    private String title;
    private String content;
    private List<ReviewImageResponse> images;
    private LocalDateTime createdAt;

    public static ReviewResponse from(Review review, Map<Long, String> mediaUrlMap) {
        Map<Long, String> safeMediaUrlMap = mediaUrlMap == null ? Map.of() : mediaUrlMap;
        return ReviewResponse.builder()
                .id(review.getId())
                .orderId(review.getOrderId())
                .itemId(review.getItemId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .title(review.getTitle())
                .content(review.getContent())
                .images(review.getImages().stream()
                        .sorted(Comparator.comparing(image -> image.getSortOrder(), Comparator.nullsLast(Integer::compareTo)))
                        .map(image -> ReviewImageResponse.from(image, safeMediaUrlMap.get(image.getMediaId())))
                        .toList())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
