package com.example.review.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
public class ReviewCreatedEvent extends DomainEvent {

    private final Long reviewId;
    private final Long orderId;
    private final Long itemId;
    private final Long userId;
    private final Integer rating;
    private final BigDecimal averageRating;
    private final Long reviewCount;

    public ReviewCreatedEvent(
            Long reviewId,
            Long orderId,
            Long itemId,
            Long userId,
            Integer rating,
            BigDecimal averageRating,
            Long reviewCount
    ) {
        super("review-events");
        this.reviewId = reviewId;
        this.orderId = orderId;
        this.itemId = itemId;
        this.userId = userId;
        this.rating = rating;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }

    @Override
    public String getEventTypeName() {
        return "REVIEW_CREATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reviewId", reviewId);
        payload.put("orderId", orderId);
        payload.put("itemId", itemId);
        payload.put("userId", userId);
        payload.put("rating", rating);
        payload.put("averageRating", averageRating);
        payload.put("reviewCount", reviewCount);
        return payload;
    }
}
