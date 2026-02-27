package com.example.notification.dto.command.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationDispatchResponse {

    @SnowflakeId
    private Long notificationId;

    private boolean deduplicated;
    private String status;
    private List<String> deliveredChannels;
    private List<String> failedChannels;

    public static NotificationDispatchResponse deduplicated(Long notificationId, String status) {
        return NotificationDispatchResponse.builder()
                .notificationId(notificationId)
                .deduplicated(true)
                .status(status)
                .deliveredChannels(List.of())
                .failedChannels(List.of())
                .build();
    }

    public static NotificationDispatchResponse created(Long notificationId,
                                                       String status,
                                                       List<String> deliveredChannels,
                                                       List<String> failedChannels) {
        return NotificationDispatchResponse.builder()
                .notificationId(notificationId)
                .deduplicated(false)
                .status(status)
                .deliveredChannels(deliveredChannels)
                .failedChannels(failedChannels)
                .build();
    }
}
