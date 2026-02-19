package com.example.notification.dto.setting.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class UpdateNotificationSettingRequest {

    @NotBlank(message = "알림 타입은 필수입니다")
    private String type;

    @NotBlank(message = "알림 채널은 필수입니다")
    private String channel;

    private Boolean enabled;
    private LocalDateTime mutedUntil;
    private Boolean clearMute;
}
