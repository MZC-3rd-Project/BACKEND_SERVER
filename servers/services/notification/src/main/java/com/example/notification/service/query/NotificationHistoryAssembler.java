package com.example.notification.service.query;

import com.example.core.pagination.CursorResponse;
import com.example.notification.dto.query.response.NotificationDeliverySummaryResponse;
import com.example.notification.dto.query.response.NotificationHistoryItemResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationHistoryAssembler {

    public CursorResponse<NotificationHistoryItemResponse> toResponse(CursorResponse<NotificationHistoryView> views) {
        List<NotificationHistoryItemResponse> items = views.getItems().stream()
                .map(this::toResponse)
                .toList();
        return CursorResponse.of(items, views.getNextCursor(), views.getTotalCount());
    }

    public NotificationHistoryItemResponse toResponse(NotificationHistoryView view) {
        return NotificationHistoryItemResponse.builder()
                .id(view.id())
                .type(view.type())
                .channel(view.channel())
                .title(view.title())
                .message(view.message())
                .referenceType(view.referenceType())
                .referenceId(view.referenceId())
                .read(view.read())
                .readAt(view.readAt())
                .createdAt(view.createdAt())
                .deliveries(view.deliveries().stream()
                        .map(delivery -> NotificationDeliverySummaryResponse.builder()
                                .channel(delivery.channel())
                                .status(delivery.status())
                                .provider(delivery.provider())
                                .attemptCount(delivery.attemptCount())
                                .lastErrorCode(delivery.lastErrorCode())
                                .lastErrorMessage(delivery.lastErrorMessage())
                                .nextRetryAt(delivery.nextRetryAt())
                                .deliveredAt(delivery.deliveredAt())
                                .build())
                        .toList())
                .build();
    }
}
