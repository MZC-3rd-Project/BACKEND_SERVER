package com.example.notification.dto.query.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class NotificationHistoryItemResponse {

    @SnowflakeId
    private Long id;

    private String type;
    private String channel;
    private String title;
    private String message;
    private String referenceType;
    private String referenceId;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    @Builder.Default
    private List<NotificationDeliverySummaryResponse> deliveries = List.of();
}
