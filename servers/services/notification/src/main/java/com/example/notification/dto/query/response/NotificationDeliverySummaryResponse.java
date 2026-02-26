package com.example.notification.dto.query.response;

import com.example.notification.entity.NotificationDelivery;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationDeliverySummaryResponse {

    private String channel;
    private String status;
    private String provider;
    private int attemptCount;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime nextRetryAt;
    private LocalDateTime deliveredAt;

    public static NotificationDeliverySummaryResponse from(NotificationDelivery delivery) {
        return NotificationDeliverySummaryResponse.builder()
                .channel(delivery.getChannel().name())
                .status(delivery.getStatus().name())
                .provider(delivery.getProvider())
                .attemptCount(delivery.getAttemptCount())
                .lastErrorCode(delivery.getLastErrorCode())
                .lastErrorMessage(delivery.getLastErrorMessage())
                .nextRetryAt(delivery.getNextRetryAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }
}
