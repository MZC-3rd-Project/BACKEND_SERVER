package com.example.notification.dto.setting.response;

import com.example.notification.entity.NotificationSetting;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationSettingItemResponse {

    private String type;
    private String channel;
    private boolean enabled;
    private LocalDateTime mutedUntil;

    public static NotificationSettingItemResponse from(NotificationSetting setting) {
        return NotificationSettingItemResponse.builder()
                .type(setting.getType().name())
                .channel(setting.getChannel().name())
                .enabled(setting.isEnabled())
                .mutedUntil(setting.getMutedUntil())
                .build();
    }
}
