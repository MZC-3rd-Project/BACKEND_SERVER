package com.example.product.consumer.review;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ReviewEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long reviewId;
    private Long orderId;
    private Long itemId;
    private Long userId;
    private Integer rating;
    private BigDecimal averageRating;
    private Long reviewCount;
}
