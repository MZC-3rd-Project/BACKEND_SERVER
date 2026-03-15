package com.example.notification.dto.query.response;

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
}
